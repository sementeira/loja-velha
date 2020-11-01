(ns loja.test-db
  (:require [clj-uuid :as uuid]
            [clojure.test :refer [deftest is use-fixtures]]
            [crux.api :as crux]
            [loja.schema :as schema]
            [loja.db-model.user :as db-user]
            [tick.alpha.api :as t]))


(defmacro throws-ex-info [expr]
  `(is (~'thrown? clojure.lang.ExceptionInfo ~expr)))


(defmacro does-not-throw [expr]
  `(is (or ~expr true)))


(def ^:dynamic *crux-node* nil)


(defn db-fixture [f]
  (with-open [crux-node (crux/start-node {})]
    (binding [*crux-node* crux-node]
      (f))))

(use-fixtures :each db-fixture)


(deftest user
  (let [user-name "test-user-name"
        hashed-password "this-would-be-a-hashed-password"
        user-id (db-user/add-user *crux-node* user-name hashed-password)
        user-id' (db-user/get-user-id *crux-node* user-name)
        user-entity (crux/entity (crux/db *crux-node*) user-id)
        other-user-name "this-user-name-does-not-exist"
        other-user-id (uuid/v1)]
    (is (db-user/user-exists? *crux-node* user-id))
    (is (not (db-user/user-exists? *crux-node* other-user-id)))
    (is (= user-id (db-user/get-user-id *crux-node* user-name)))
    (is (nil? (db-user/get-user-id *crux-node* other-user-name)))
    (is (= hashed-password (db-user/get-hashed-password *crux-node* user-id)))
    (is (nil? (db-user/get-hashed-password *crux-node* other-user-id)))
    (is (= user-id user-id'))
    (is (schema/user? user-entity))
    (is (= {:crux.db/id user-id
            :loja.user/name user-name
            :loja.user/hashed-password hashed-password}
           user-entity))
    (throws-ex-info (db-user/add-user *crux-node* user-name "some-password"))
    (does-not-throw (db-user/add-user *crux-node* other-user-name "some-password"))))


(defn- add-test-user []
  (db-user/add-user *crux-node* "test-user-name" "hashed-password"))



(comment

  )
