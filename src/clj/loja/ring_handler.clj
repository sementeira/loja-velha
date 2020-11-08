(ns loja.ring-handler
  (:require [better-cond.core :as b]
            [buddy.auth :refer [authenticated?]]
            [buddy.auth.accessrules :refer [restrict]]
            [buddy.auth.backends.session :refer [session-backend]]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
            [buddy.hashers :as hashers]
            [clojure.stacktrace :refer [print-stack-trace]]
            [hiccup.core :refer [html]]
            [hiccup.page :refer [doctype]]
            [integrant.core :as ig]
            [muuntaja.core :as muuntaja]
            [muuntaja.middleware :refer [wrap-format]]
            [reitit.ring :as rring]
            [loja.db-model.user :as db-user]
            [loja.system :refer [load-config]]
            [ring.middleware.anti-forgery :as csrf :refer [*anti-forgery-token*]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.util.http-response :refer [content-type ok see-other]]
            [clojure.java.io :as io])
  (:import [com.stripe Stripe]
           [com.stripe.model.checkout Session]
           [com.stripe.net Webhook]
           [com.stripe.param.checkout
            SessionCreateParams
            SessionCreateParams$LineItem
            SessionCreateParams$LineItem$PriceData
            SessionCreateParams$LineItem$PriceData$ProductData
            SessionCreateParams$Mode
            SessionCreateParams$PaymentMethodType]))


(def stripe-endpoint-secret "whsec_qk7iyYZ0PjqsRGdZOzmzPkJHdI64DpFH")

(let [qty (atom 0)]
  (defn- create-stripe-checkout-session []
    (let [{{:keys [public-key secret-key]} :stripe} (load-config "dev-config.clj")
          _ (set! Stripe/apiKey secret-key)
          params
          (.. (SessionCreateParams/builder)
              (addPaymentMethodType SessionCreateParams$PaymentMethodType/CARD)
              (setMode SessionCreateParams$Mode/PAYMENT)
              (setSuccessUrl "http://localhost:3000/stripe?success=true")
              (setCancelUrl "http://localhost:3000/stripe?cancel=true")
              (addLineItem
               (.. (SessionCreateParams$LineItem/builder)
                   (setQuantity (swap! qty inc))
                   (setPriceData
                    (.. (SessionCreateParams$LineItem$PriceData/builder)
                        (setCurrency "eur")
                        (setUnitAmount 2000)
                        (setProductData
                         (.. (SessionCreateParams$LineItem$PriceData$ProductData/builder)
                             (setName "Camisola")
                             (build)))
                        (build)))
                   (build)))
              (build))
          session (Session/create params)]
      (.getId session))))


(comment

  (create-stripe-checkout-session)

  )

(defn- html5-ok
  ([title body]
   (html5-ok title [] body))
  ([title head body]
   (-> (html
        (doctype :html5)
        `[:html
          [:head
           [:meta {:charset "UTF-8"}]
           [:meta {:name "viewport"
                   :content "width=device-width, initial-scale=1.0"}]
           [:title ~title]
           ~@head]
          [:body
           ~@body]])
       ok
       (content-type "text/html; charset=utf-8"))))

(defn- frontpage [_]
  (html5-ok "Loja da Semente"
            [[:link {:rel "stylesheet" :href "https://cdn.jsdelivr.net/font-hack/2.020/css/hack-extended.min.css"}]
             [:link {:rel "stylesheet" :href "https://fonts.googleapis.com/css?family=Yrsa"}]
             [:link {:rel "stylesheet" :href "https://fonts.googleapis.com/css?family=Roboto:400,300,500&amp;subset=latin" :media "all"}]
             [:script {:type "text/x-mathjax-config"}
              "MathJax.Hub.Config({asciimath2jax: {delimiters: [['¡','¡']]}});"]
             [:script#MathJax-script
              {:type "text/javascript"
               :async true
               :src "https://cdn.jsdelivr.net/npm/mathjax@2/MathJax.js?config=AM_CHTML"}]]
            [[:div#app]
             [:script {:type "text/javascript"}
              (str "var csrfToken = \""
                   *anti-forgery-token*
                   "\";")]
             [:script {:type "text/javascript"
                       :src "/js/loja.js"}]]))

(defn- login [redirect-to]
  (html5-ok "Login"
            [[:form {:method "post"
                     :action "/login"}
              [:input {:type "hidden" :name "csrf-token" :value *anti-forgery-token*}]
              [:input {:type "hidden" :name "redirect-to" :value redirect-to}]
              [:div
               [:label "Name"
                [:input {:type "text" :name "name" :required true}]]]
              [:div
               [:label "Password"
                [:input {:type "password" :name "password" :required true}]]]
              [:div
               [:input {:type "submit" :value "Login"}]]]]))


(defn on-error [request _]
  (->
   {:status 403
    :body (str "Access to " (:uri request) " is not authorized")}
   (content-type "text/plain; charset=utf-8")))


(defn wrap-restricted [handler]
  (restrict handler {:handler authenticated?
                     :on-error on-error}))


(defn wrap-auth [handler]
  (let [backend (session-backend)]
    (-> handler
        (wrap-authentication backend)
        (wrap-authorization backend))))


(defn- credentials->user [crux-node user-name password]
  (b/cond
    :when-let [user-id (db-user/get-user-id crux-node user-name)
               hashed-password (db-user/get-hashed-password crux-node user-id)]
    :when (hashers/check password hashed-password)
    user-id))


(defn- stripe-test [req]
  (def aft *anti-forgery-token*)
  (html5-ok "Stripe test"
            [[:script {:src "https://js.stripe.com/v3/"}]]
            [[:div#app]
             [:script {:type "text/javascript"}
              (str "var csrfToken = \""
                   *anti-forgery-token*
                   "\";")]
             [:script {:type "text/javascript"
                       :src "/js/loja.js"}]]))


(defn login-handler [crux-node]
  (fn handle-login [{{:keys [name password redirect-to]} :params
                     :as req}]
    (if-let [user-id (credentials->user crux-node name password)]
      (-> (see-other redirect-to)
          (assoc :session (assoc (:session req) :identity user-id)))
      ;; XXX: set error string in login instead
      (on-error {:uri redirect-to} nil))))

(defn- handle-stripe-webhook [{:keys [headers body]
                               :as req}]
  (def wh-req req)
  (let [sig (get headers "stripe-signature")
        _ (prn "sig" sig)
        bodystr (slurp body)
        event (try (Webhook/constructEvent bodystr sig stripe-endpoint-secret)
                   (catch Throwable t
                     (println "Error!")
                     (print-stack-trace t)
                     :error))]
    (prn "got event" event)
    (def stripe-evt event)
    {:status (if (identical? event :error) 400 200)}))


(defn wrap-def [handler]
  (fn [req]
    (def original-req req)
    (handler req)))


(defn- wrap-clojurize [handler]
  (-> handler
      wrap-auth
      wrap-session
      wrap-format

      wrap-keyword-params
      wrap-params))


(defn- wrap-anti-forgery [handler]
  (csrf/wrap-anti-forgery
   handler
   {:read-token (fn [req]
                  (or (get-in req [:params :csrf-token])
                      (get-in req [:body-params :csrf-token])))}))


(defn- handler [crux-node]
  (rring/ring-handler
   (rring/router
    [["/stripe-webhook" {:post handle-stripe-webhook}]
     ["" {:middleware [wrap-clojurize]}
      ["/stripe-session" {:post (constantly (ok {:stripe/session-id (create-stripe-checkout-session)}))}]
      ["" {:middleware [wrap-anti-forgery]}
       ["/stripe" {:get stripe-test}]
       ["/login" {:get #(-> % :params :redirect-to login)
                  :post (login-handler crux-node)}]
       [""
        #_{:middleware [wrap-restricted]}
        ["/initial-data" {:get (constantly (ok {:test/data 42}))}]
        ["/test" {:get (constantly {:status 200
                                    :headers {"Content-Type" "text/plain"}
                                    :body "OK"})}]]]]])
   (identity
    #_wrap-restricted
    (wrap-clojurize
     (rring/routes
      (rring/create-resource-handler
       {:path "/"})
      (rring/create-default-handler
       {:not-found frontpage}))))))


(defmethod ig/init-key ::handler
  [_ {:keys [crux-node]}]
  (handler crux-node))


(comment
  (do
    (require '[integrant.repl.state :refer [system]])
    (def crux-node (:loja.crux/node system)))

  crux-node

  original-req

  aft

  ( (save-qa-handler crux-node)
   inner-req)

  original-req

  wh-req

  (def header (-> wh-req :headers (get "stripe-signature")))
  (def body (:body wh-req))

  (def body-params (:body-params wh-req))

  (keys body-params)
  (def body-json (slurp (muuntaja/encode "application/json" body-params)))

  (println body-json)


  (slurp body)

  (Webhook/constructEvent body-json header stripe-endpoint-secret)
  reqreq

  (def handler *1)
  (def handler (rring/ring-handler handler))
  (handler {:uri "/stripe-session" :request-method :post :params {:csrf-token aft}})

  (*1 :blah)
  (require '[loja.crux :as c])
  (c/q1 crux-node {:find ['qa]
                   :where [['qa :loja.qa/owner]]})
  (defn add-user [crux-node name password]
    (db-user/add-user crux-node name (hashers/derive password)))

  (add-user crux-node "outro" "senha")

  reqreq

  )
