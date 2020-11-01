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
   [:loja.user/hashed-password nonempty-string]])
(def user? (m/validator user))



(comment

  (require '[clj-uuid :as uuid])

  (user? {:crux.db/id (uuid/v1)
          :loja.user/name "es"
          :loja.user/hashed-password "abcde123"
          :loja.user/of-matrix [1.0 2.0 3.0]})

  )
