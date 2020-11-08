(ns loja.stripe-test
  (:require
   [loja.transit-util :refer [transit-ajax-request-format
                              transit-ajax-response-format]]
   [kee-frame.core :as kf]
   [re-frame.core :as rf]))


(def stripe-public-key "pk_test_eWRfT83OzlFPlvCgWGGlKAnB")
(def stripe (js/Stripe stripe-public-key))


(rf/reg-fx
 ::stripe-redirect
 (fn [{:keys [stripe/session-id]}]
   (println "result si" (.redirectToCheckout stripe #js {:sessionId session-id}))))


(kf/reg-chain
 ::get-stripe-session
 (fn [_ _]
   {:http-xhrio {:method          :post
                 :uri             "/stripe-session"
                 :format transit-ajax-request-format
                 :params {:csrf-token js/csrfToken}
                 :response-format transit-ajax-response-format
                 :on-failure      [:common/set-error]}})
 (fn [{:keys [db]} [{:keys [stripe/session-id]}]]
   {:db (assoc db :stripe/session-id session-id)
    ::stripe-redirect {:stripe/session-id session-id}}))


(defn stripe-test []
  (let [stripe-session @(rf/subscribe [:db/path [:stripe/session-id]])]
    [:div (if stripe-session (str "Stripe session is " stripe-session) "No stripe session.")
     [:div
      [:button#checkout-button {:on-click #(rf/dispatch [::get-stripe-session])}"Checkout"]]]))
