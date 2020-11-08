(ns loja.db-model.user
  (:require [clj-uuid :as uuid]
            [loja.crux :refer [q1 sync-tx]]
            [loja.schema :refer [user?]]
            [crux.api :as crux]))


(defn get-user-id [crux-node name]
  (q1 crux-node
      {:find ['u]
       :where [['u :loja.user/name name]]}))


(defn get-hashed-password [crux-node user-id]
  (q1 crux-node
      {:find ['p]
       :where [[user-id :loja.user/hashed-password 'p]]}))


(defn user-exists? [crux-node user-id]
  (some? (get-hashed-password crux-node user-id)))


(defn add-user [crux-node name hashed-password role]
  (when-let [uid (get-user-id crux-node name)]
    (throw (ex-info "user already exists" {:name name :uid uid})))
  (let [uid (uuid/v1)
        user {:crux.db/id uid
              :loja.user/name name
              :loja.user/role role
              :loja.user/hashed-password hashed-password}]
    (if (user? user)
      (and (sync-tx crux-node
                [[:crux.tx/match uid nil]
                 [:crux.tx/put user]])
           uid)
      (throw (ex-info "invalid user?" {:user user})))))


(comment

  (do
    (require '[crux.api :as crux])
    (def crux-node (crux/start-node {}))

    )

  (def uid (get-user-id crux-node "es"))
  (get-hashed-password crux-node uid)

  (add-user crux-node "lhou" "senhahashada" :customer)

  (get-hashed-password crux-node (get-user-id crux-node "lhou"))
  )
