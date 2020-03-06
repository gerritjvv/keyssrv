(ns keyssrv.test.docker-utils
  (:require [clojure.test :refer :all]
            [keyssrv.test.sh :as sh]
            [keyssrv.test.utils :as test-utils]))


;;; These were used to run docker-compose dbs, but we find it too flaky
;;;; and therefore are going to try and pre-allocate the dbs per test runs
;;;;; and control which tests run with which dbs per environment variables

;(defn run-compose [compose-file & args]
;  {:pre [(string? compose-file)]}
;  (apply sh/sh-async {} (concat ["docker-compose"
;                                 "-f" compose-file] args)))
;
;(defn run-compose-up [compose-file srv]
;  {:pre [(string? compose-file) (string? srv)]}
;  (run-compose compose-file "up" srv))
;
;(defn run-compose-stop [sh-async-ret]
;  (sh/kill-cmd sh-async-ret))
;
;
;(defn db-down [db-ret]
;  (run-compose-stop db-ret))
;
;(defn db-up
;  "Starts a db and waits for its port to open
;  If the port doesn't open a timeout exception is thrown
;  ret: The command context, use it with db-down to stop the db"
;  [compose-file db-srv port & {:keys [timeout-ms]}]
;  (let [sh-resp (run-compose-up compose-file db-srv)]
;
;    (test-utils/wait-for-port-open! (or timeout-ms 20000) port)
;    sh-resp))


(defn with-db
  "Run's db-up, then f as (f) and after db-down"
  [db-srv port f]
  {:pre [(string? db-srv) (integer? port) (fn? f)]}
  (f))