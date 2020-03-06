(ns kuser
  (:require [keyssrv.config :refer [env]]
            [clojure.spec.alpha :as s]
            [expound.alpha :as expound]
            [mount.core :as mount]
            [keyssrv.core :refer [start-app]]
            [keyssrv.db.core]
            [keyssrv.config :as conf]
            [keyssrv.tokens.core :as tokens]
            [keyssrv.product :as product]
            [conman.core :as conman]
            [luminus-migrations.core :as migrations]))

(alter-var-root #'s/*explain-out* (constantly expound/printer))

(defn migrate []
      (migrations/migrate ["migrate"] (select-keys env [:database-url])))

(defn start []
  (mount/start #'conf/env #'keyssrv.db.core/*db*)

  (migrate)

  (mount/start #'tokens/DefaultTokenStore)

  (product/setup-stripe-products)
  (mount/start-without #'keyssrv.core/repl-server))

(defn stop []
  (mount/stop-except #'keyssrv.core/repl-server))

(defn restart []
  (stop)
  (Thread/sleep 1000)
  (start))

(def r restart)

(defn restart-db []
  (mount/stop #'keyssrv.db.core/*db*)
  (mount/start #'keyssrv.db.core/*db*)
  (binding [*ns* 'keyssrv.db.core]
    (conman/bind-connection keyssrv.db.core/*db* "sql/queries.sql")))

(defn reset-db []
  (migrations/migrate ["reset"] (select-keys env [:database-url])))

(defn rollback []
  (migrations/migrate ["rollback"] (select-keys env [:database-url])))

(defn create-migration [name]
  (migrations/create name (select-keys env [:database-url])))

