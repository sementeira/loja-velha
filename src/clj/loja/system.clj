(ns loja.system
  (:require [buddy.hashers :as hashers]
            [clojure.java.io :as io]
            [hashp.core]                ; to enable #p data readers
            [integrant.core :as ig]
            integrant.repl
            [loja.data-readers :as data-readers]
            [loja.db-model.user :as db-user]
            [taoensso.timbre :as timbre :refer [info warn]]
            [taoensso.timbre.tools.logging :refer [use-timbre]]))


(defonce ^:private readers-inited
  (do (data-readers/patch!)
      true))


(defn init-logging [level]
  (add-tap (bound-fn* clojure.pprint/pprint))
  (use-timbre)
  (timbre/swap-config!
   assoc :min-level
   [[#{"loja.*"} level]
    [#{"*"} :info]]))


(defn load-config [filename]
  (read-string (slurp (io/file filename))))


(defmethod ig/init-key ::init [_ {:keys [crux-node]
                                  {:keys [username password role]} :user}]
  (db-user/add-user crux-node username (hashers/derive password) role))


(defn prep [{:keys [db-dir init-user http-port nrepl-port]}]
  (let [system-cfg
        (cond-> {:loja.crux/node {:dir db-dir}
                 :loja.ring-handler/handler
                 {:crux-node (ig/ref :loja.crux/node)}
                 :loja.jetty/server
                 {:port http-port
                  :handler (ig/ref :loja.ring-handler/handler)}}
          nrepl-port (assoc :loja.nrepl/server {:port nrepl-port})
          init-user (assoc :loja.system/init {:crux-node (ig/ref :loja.crux/node)
                                                  :user init-user}))]
    (info "System config:" (pr-str system-cfg))
    (ig/load-namespaces system-cfg)
    (integrant.repl/set-prep! (constantly system-cfg))))


(comment


  )
