(ns loja.schema
  (:require [malli.core :as m]
            [malli.registry :as mr]
            [tick.alpha.api :as t :refer [date?]]))


(def registry
  (let [schema (m/-simple-schema {:type 'date? :pred date?})]
    (mr/registry
     (-> (m/default-schemas)
         (assoc 'date? schema)
         (assoc date? schema)))))


(def nonempty-string [:string {:min 1}])


(def user
  [:map
   [:crux.db/id uuid?]
   [:loja.user/name nonempty-string]
   [:loja.user/hashed-password nonempty-string]
   [:loja.user/of-matrix {:optional true} [:vector double?]]])
(def user? (m/validator user))


(def qa
  [:map
   [:crux.db/id uuid?]
   [:loja.qa/owner uuid?]
   [:loja.qa/question nonempty-string]
   [:loja.qa/answer nonempty-string]])
(def qa? (m/validator qa))


(def lembrando
  [:map
   [:crux.db/id uuid?]
   [:loja.lembrando/qa uuid?]
   [:loja.lembrando/due-date date?]
   [:loja.lembrando/failing? boolean?]
   [:loja.lembrando/remembering-state {:optional true} [:vector double?]]])
(def lembrando?
  (m/validator lembrando {:registry registry}))


(def recall
  [:map
   [:crux.db/id uuid?]
   [:loja.recall/user uuid?]
   [:loja.recall/lembrando uuid?]
   [:loja.recall/rate pos-int?]])
(def recall? (m/validator recall))


(comment

  (require '[clj-uuid :as uuid])

  (user? {:crux.db/id (uuid/v1)
          :loja.user/name "es"
          :loja.user/hashed-password "abcde123"
          :loja.user/of-matrix [1.0 2.0 3.0]})

  (lembrando? {:crux.db/id (uuid/v1)
               :loja.lembrando/qa (uuid/v1)
               :loja.lembrando/due-date (t/date)
               :loja.lembrando/failing? true
               :loja.lembrando/remembering-state [1.0 2.0 3.0]})

  (recall? {:crux.db/id (uuid/v1)
            :loja.recall/user (uuid/v1)
            :loja.recall/lembrando (uuid/v1)
            :loja.recall/rate 3})
  )
