(ns keyssrv.db.core
  (:require
    [keyssrv.db.db_utils :as db-utils]
    [cheshire.core :refer [generate-string parse-string]]
    [clj-time.jdbc]
    [clojure.tools.logging :as log]
    [conman.core :as conman]
    [keyssrv.config :refer [env]]
    [mount.core :refer [defstate]]
    [mount.core :as mount]))

(defstate ^:dynamic *db*
  :start (do
          (mount/start #'env)                               ;ensure env is started
           (if-let [jdbc-url (env :database-url)]
             (conman/connect! {:jdbc-url jdbc-url
                               :maximum-pool-size 10})
             (do
               (log/warn "database connection URL was not found, please set :database-url in your config, e.g: dev-config.edn")
               *db*)))
  :stop (conman/disconnect! *db*))

(conman/bind-connection *db* "sql/queries.sql")
(conman/bind-connection *db* "sql/wizzard-data.sql")


(db-utils/register-hub-sql-hyphenated)
(db-utils/expend-pg-data-types)


(defn with-transaction [f]
  (conman/with-transaction [*db*]
                           (f *db*)))