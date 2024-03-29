(ns ^:figwheel-hooks loja.core
  (:require [kee-frame.core :as kf]
            [re-frame.core :as rf]
            [loja.edit :refer [edit]]
            [loja.review :refer [review]]
            [loja.transit-util :refer (transit-ajax-response-format)]))


(kf/reg-chain
 ::load-initial-data
 (fn [_ _]
   {:http-xhrio {:method          :get
                 :uri             "/initial-data"
                 :response-format transit-ajax-response-format
                 :on-failure      [:common/set-error]}})
 (fn [{:keys [db]} [_ initial-data]]
   {:db (merge db initial-data)}))


(kf/reg-controller
 ::load-initial-data-on-startup
 {:params (constantly true)
  :start  [::load-initial-data]})


(rf/reg-sub
 :nav/page
 :<- [:kee-frame/route]
 (fn [route _]
   (-> route :data :name)))


(rf/reg-event-db
 :common/set-error
 (fn [db error]
   (assoc db :common/error error)))


(rf/reg-event-db
 :db/set-key
 (fn [db [_ k v]]
   (assoc db k v)))


(rf/reg-event-db
 :db/del-key
 (fn [db [_ k]]
   (dissoc db k)))


(rf/reg-sub
 :db/path
 (fn [db [_ path]]
   (get-in db path)))


(def routes
  [["/" :review]
   ["/edit/:qa" :edit]
   ["/not-found" :not-found]])


(def page-names
  [[:review "Review"]
   [:edit "Edit question"]])


(defn not-found []
  [:div
   [:h1 "WAT"]
   [:p "Not found, 404, etc."]])


(kf/reg-controller
 :edit
 {:params (fn [{:keys [path-params]
                {:keys [name]} :data}]
            (when (= name :edit)
              (:qa path-params)))
  :start (fn [_ id]
           (if (= id "new")
             [:db/del-key :edit/qa]
             [:db/set-key :edit/qa (uuid id)]))})


(defn navigation [current-page qa]
  [:nav
   [:ul
    (for [[page-id page-name] page-names]
      ^{:key (name page-id)}
      [:li
       (if (= current-page page-id)
         [:strong page-name]
         [:a {:href (kf/path-for
                     (cond-> [page-id]
                       (= page-id :edit) (conj {:qa (or qa :new)})))}
          page-name])])]])


(defn root []
  (let [current-page @(rf/subscribe [:nav/page])
        qa @(rf/subscribe [:db/path [:edit/qa]])
        error @(rf/subscribe [:db/path [:common/error]])]
    [:div
     (when error
       [:div [:pre [:code {:style {:color :red}}
                    (with-out-str (cljs.pprint/pprint error))]]])
     [navigation current-page qa]
     [:main
      (case current-page
        :review [review]
        :edit [edit qa]
        :not-found [not-found]
        [:div "Loading..."])]]))


(defn ^:after-load mount!
  ([] (mount! true))
  ([debug?]
   (rf/clear-subscription-cache!)
   (kf/start! {:debug? (boolean debug?)
               :routes routes
               :not-found "/not-found"
               :initial-db {}
               :root-component [root]})))


(defn init! [debug?]
  ;; one-off (not reloaded) initialization would go here.
  (mount! debug?))
