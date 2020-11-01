(ns loja.db-model.qa
  (:require [better-cond.core :as b]
            [clj-uuid :as uuid]
            [crux.api :as crux]
            [loja.crux :refer [q1 sync-tx]]
            [loja.db-model.user :as db-user]
            [loja.schema :refer [qa? lembrando?]]
            [tick.alpha.api :as t]))


(defn existing-question [crux-node user-id question]
  (q1 crux-node
      {:find ['qa]
       :where [['qa :loja.qa/owner user-id]
               ['qa :loja.qa/question question]]}))


(defn initial-lembrando [qa-id]
  {:crux.db/id (uuid/v1)
   :loja.lembrando/qa qa-id
   :loja.lembrando/due-date (t/today)
   :loja.lembrando/failing? false
   ;; No initial :loja.lembrando/remembering-state since the representation
   ;; of this is private to the spaced-repetition library.
   })


;; XXX: allow editing existing qa
(defn add-qa [crux-node user-id question answer]
  (let [id (uuid/v1)
        qa {:crux.db/id id
            :loja.qa/owner user-id
            :loja.qa/question question
            :loja.qa/answer answer}]
    (b/cond
      (not (qa? qa))
      (throw (ex-info "invalid question/answer pair?" {:qa qa}))
      (not (db-user/user-exists? crux-node user-id))
      (throw (ex-info "unknown user?" {:user-id user-id}))
      :let [eq (existing-question crux-node user-id question)]
      eq
      (throw (ex-info "question exists for user"
                      {:question question
                       :existing-qa eq
                       :user-id user-id}))
      :else (and (sync-tx crux-node
                          [[:crux.tx/match id nil]
                           [:crux.tx/put qa]
                           [:crux.tx/put (initial-lembrando id)]])
                 id))))


(defn update-qa [crux-node
               old
               {:keys [loja.qa/owner
                       loja.qa/question
                       crux.db/id] :as new}]
  (let [eq (existing-question crux-node owner question)]
    (cond
      (not (qa? new))
      (throw (ex-info "bad new question/answer pair:"
                      {:new-qa new}))
      (and eq (not= eq id))
      (throw (ex-info "question exists for user"
                      {:question question
                       :existing-qa eq
                       :user-id owner}))
      :else
      (sync-tx crux-node
               [[:crux.tx/match id old]
                [:crux.tx/put new]]))))


(defn qa-exists? [crux-node qa-id]
  (some?
   (q1 crux-node
       {:find ['o]
        :where [[qa-id :loja.qa/owner 'o]]})))


(defn check-qa [crux-node qa]
  (when-not (qa-exists? crux-node qa)
    (throw (ex-info "unknown question/answer pair?" {:qa qa}))))


(defn qa->lembrando-id [crux-node qa]
  (q1 crux-node
      {:find ['l]
       :where [['l :loja.lembrando/qa qa]]}))


(defn update-lembrando [crux-node old {:keys [crux.db/id]
                                       :as new}]
  (cond
    (not (lembrando? new))
    (throw (ex-info "invalid lembrando" {:lembrando new}))
    :else (sync-tx crux-node
                   [[:crux.tx/match id old]
                    [:crux.tx/put new]])))


(defn due-questions [crux-node user-id]
  (->> (crux/q (crux/db crux-node)
               {:find '[(eql/project qa-id [*])]
                :where '[[qa-id :loja.qa/owner user-id]
                         [lid :loja.lembrando/qa qa-id]
                         [lid :loja.lembrando/due-date d]
                         [(tick.alpha.api/<= d today)]]
                :args [{'user-id user-id
                        'today (t/today)}]})
       (map first)
       set))

(comment

  (t/today)
  (do
    (require '[crux.api :as crux])
    (require '[loja.db-model.user :as db-user])
    (def crux-node (crux/start-node {})))


  (def uid (db-user/add-user crux-node "es" "hp"))
  (def qa1 (add-qa crux-node uid "que?" "pois"))
  (def qa2 (add-qa crux-node uid "que que?" "pois pois"))

  (due-questions crux-node uid)

  (def old-l2 (crux/entity (crux/db crux-node) (qa->lembrando-id crux-node qa2)))
  (update-lembrando crux-node
                    old-l2
                    (assoc old-l2 :loja.lembrando/due-date (t/date "2222-01-01")))

  (due-questions crux-node uid)
  ;; throws "unknown user"
  (add-qa crux-node (uuid/v1) "ha?" "já")

  (require '[crux.api :as crux])
  (require '[loja.crux :as rc])

  (def id
    (rc/q1 crux-node
          {:find ['id]
           :where [['id :loja.qa/owner]]}))

  (def e (crux/entity (crux/db crux-node) id))

  (qa? e)

  (type (ex-info "blah" {:x 1}))

  clojure.lang.ExceptionInfo

  (qa-exists? crux-node id)
  (qa-exists? crux-node (uuid/v1))
  )
