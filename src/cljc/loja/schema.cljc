(ns loja.schema
  (:require [malli.core :as m]
            [malli.registry :as mr]
            #?(:clj [clj-uuid :as uuid])
            [tick.alpha.api :as t :refer [date?]]))


(def registry
  (let [schema (m/-simple-schema {:type 'date? :pred date?})]
    (mr/registry
     (-> (m/default-schemas)
         (assoc 'date? schema)
         (assoc date? schema)))))


;; nonempty string
(def nestr [:string {:min 1}])


(def user
  [:map
   [:crux.db/id uuid?]
   [:loja.user/name nestr]
   [:loja.user/role [:enum :customer :shopkeeper]]
   [:loja.user/hashed-password nestr]])

(def user? (m/validator user))

(comment
  (user? {:crux.db/id (uuid/v1)
          :loja.user/name "es"
          :loja.user/role :shopkeeper
          :loja.user/hashed-password "somepasshash"})
  )


(def address
  [:map
   [:crux.db/id uuid?]
   [:loja.address/user uuid?]
   [:loja.address/name nestr]
   [:loja.address/street nestr]
   [:loja.address/number&c nestr]
   [:loja.address/city nestr]
   [:loja.address/postal-code nestr]
   [:loja.address/province nestr]
   [:loja.address/country nestr]])

(def address? (m/validator address))

(comment
  (address?
   {:crux.db/id (uuid/v1)
    :loja.address/user (uuid/v1)
    :loja.address/name "Manolo Peres Deus"
    :loja.address/street "Rua Nova de Riba"
    :loja.address/number&c "8 portal 2 4B"
    :loja.address/city "Santiago de Compostela"
    :loja.address/postal-code "15701"
    :loja.address/province "A Corunha"
    :loja.address/country "Galiza"})
  )


(def category
  [:map
   [:crux.db/id keyword?]
   [:loja.category/name nestr]
   [:loja.category/description nestr]])

(def category? (m/validator category))

(comment
  (category? {:crux.db/id :camisolas
              :loja.category/name "camisolas"
              :loja.category/description "Camisolas som camisolas"})
  )


(def product
  [:map
   [:crux.db/id uuid?]
   [:loja.product/category keyword?]
   [:loja.product/name nestr]
   [:loja.product/description nestr]])

(def product? (m/validator product))

(comment
  (product? {:crux.db/id (uuid/v1)
             :loja.product/category :camisolas
             :loja.product/name "Camisola ramalho"
             :loja.product/description "Umha camisola com um ramalho desenhado"})
  )


(def color-val [:int {:min 0 :max 255}])

(def color
  [:map
   [:crux.db/id keyword?]
   [:loja.color/name nestr]
   [:loja.color/rgb [:map [:r color-val] [:g color-val] [:b color-val]]]])

(def color? (m/validator color))

(comment
  (color? {:crux.db/id :vermelho
           :loja.color/name "vermelho"
           :loja.color/rgb {:r 255 :g 0 :b 0}})
  )


(def size
  [:map
   [:crux.db/id keyword?
    :loja.size/name nestr]])

(def size? (m/validator size))

(comment
  (size? {:crux.db/id :s
          :loja.size/name "Pequeno"}))


(def merc
  [:map
   [:crux.db/id uuid?]
   [:loja.merc/name nestr]
   [:loja.merc/category keyword?]
   [:loja.merc/color {:optional true} keyword?]
   [:loja.merc/size {:optional true} keyword?]
   [:loja.merc/quantity integer?]])

(comment

  (require '[clj-uuid :as uuid])

  (user? {:crux.db/id (uuid/v1)
          :loja.user/name "es"
          :loja.user/hashed-password "abcde123"
          :loja.user/of-matrix [1.0 2.0 3.0]})

  )
