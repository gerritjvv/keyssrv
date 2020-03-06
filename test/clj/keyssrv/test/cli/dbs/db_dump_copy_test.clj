(ns
  ^{:doc "
  Test the pk db [dump|copy] commands

  The focus here is to see that we can export and import data (including binary columns) successfully

   We test postgres and mysql.

   The data dumped should use the \\x[hex] notation format.
   Postgres understands this as binary, but mysql needs extra help via a variable, unhex and remove the leading.

  "}
  keyssrv.test.cli.dbs.db-dump-copy-test
  (:require [clojure.test :refer :all]
            [keyssrv.test.cli.utils :as utils]
            [keyssrv.test.utils :as test-utils]
            [keyssrv.config :as conf]
            [keyssrv.test.pkcli :as pkcli]
            [keyssrv.test.api.utils :as apiutils]
            [clj-time.format :as f]
            [clj-time.core :as t]
            [sjdbc.core :as sjdbc]
            [keyssrv.test.encrypt :as encrypt])
  (:import (org.apache.commons.lang3 StringUtils)
           (java.nio.file Files)
           (java.nio.file.attribute FileAttribute)))


(def -POSTGRES-HOST (delay (if (conf/bitbucket?) "localhost" "postgres")))
(def -POSTGRES-DB (delay (if (conf/bitbucket?) "test" "keyssrv")))

(def -MYSQL-HOST (delay (if (conf/bitbucket?) "localhost" "mysql")))
(def -MYSQL-DB (delay @-POSTGRES-DB))


(def DATABASES (delay [
                       {:hosted-on        "local"
                        :type             "postgres"
                        :host             @-POSTGRES-HOST
                        :create-table-sql (fn [table]
                                            (str "CREATE TABLE " table " (id INT, name varchar, status int, start_date timestamp, data bytea)"))
                        :dbuser           "test"
                        :password         "test"
                        :database         @-POSTGRES-DB
                        :port             5432
                        :sjdbc-conn       (delay
                                            (sjdbc/open (str
                                                          "jdbc:postgresql://"
                                                          @-POSTGRES-HOST "/" @-POSTGRES-DB
                                                          "?user=test&password=test") {}))}

                       {:hosted-on        "local"
                        :type             "mysql"
                        :create-table-sql (fn [table]
                                            (str "CREATE TABLE " table " (id INT, name varchar(255), status int, start_date datetime, data blob)"))

                        :host             @-MYSQL-HOST
                        :dbuser           "test"
                        :password         "test"
                        :database         @-MYSQL-DB
                        :port             3306
                        :sjdbc-conn       (delay
                                            (sjdbc/open (str
                                                          "jdbc:mysql://"
                                                          @-MYSQL-HOST "/" @-MYSQL-DB
                                                          "?user=test&password=test") {}))}

                       ]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;; Test fixtures

(defn setup [f]
  (utils/setup f))

(use-fixtures :once setup)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;; Utils


(def built-in-formatter (f/formatters :basic-date-time))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;; Test Data and Data Setup

(defn create-table! [{:keys [sjdbc-conn create-table-sql] :as conf} table]
  {:pre [sjdbc-conn]}
  (prn "sjdbc-conn " @sjdbc-conn)
  ;;id INT, name varchar, status int, start_date timestamp, data bytea
  (sjdbc/exec @sjdbc-conn (create-table-sql table)))

;; IMPORTANT! : the data column is the unique generated name encrypted
;;
(defn insert-test-data! [{:keys [sjdbc-conn]} table n]
  {:pre [sjdbc-conn]}

  (doseq [i (range 0 n)]

    (let [name (test-utils/unique-str)]
      (sjdbc/exec @sjdbc-conn
                  (str "INSERT INTO " table " (id, name, status, start_date, data) VALUES(?, ?, ?, ?, ?)")
                  i name (rand-int 200) (t/now) (encrypt/encrypt name)))))

(defn select-data [{:keys [sjdbc-conn]} table]
  {:pre [sjdbc-conn]}
  (sjdbc/query @sjdbc-conn (str "select * from " table " ORDER BY name")))

(defn decrypt-data [record]
  (update record :data encrypt/decrypt))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;; Test methods

(defn first-numeric ^String [vars]
  (str (first (filter #(StringUtils/isNumeric (str %)) vars))))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;; Test harnesses

;;; Test that we can dump a table
;;; and then copy it back again
;;; we take care to test dates and binary values
(defn db-cli-dump-copy-tests [db-conf pk-runner safe lbl]
  (let [table1 (str "test_table_" (System/nanoTime))
        table2 (str "test_table_" (System/nanoTime))

        _ (create-table! db-conf table1)
        _ (create-table! db-conf table2)

        ;; n can be low, we are testing functionality
        _ (insert-test-data! db-conf table1 100)
        tmp-dir (.toAbsolutePath (Files/createTempDirectory "db_cli_copy" (into-array FileAttribute [])))
        ]

    (pk-runner
      "db"
      "dump"
      "-s" safe
      "-n" lbl
      "-t" table1
      "-d" tmp-dir)

    (pk-runner
      "db"
      "copy"
      "-s" safe
      "-n" lbl
      "-k"
      "-t" table2
      "-f" (str tmp-dir "/" table1 ".dat.gz"))
    ;
    ;;; select data from table1 and table2, then compare each record
    (let [data1 (map decrypt-data (select-data db-conf table1))
          data2  (map decrypt-data (select-data db-conf table2))]

      (doseq [[r1 r2] (map list data1 data2)]
        (is
          (= r1 r2))
        ;; there are allot of records, we exit if compare fails to avoid failure storm in logs
        (when (not= r1 r2)
          (throw (RuntimeException. (str r1 " != " r2))))))

    ))

(defn run-db-test [db-conf group {:keys [key-id key-secret]} {:keys [lbl]}]
  (let [auth-record {:PK-KEY-ID     key-id
                     :PK-KEY-SECRET key-secret}

        pk-runner (fn [& args]
                    (apply pkcli/run auth-record args))

        safe (:name group)]

    (db-cli-dump-copy-tests db-conf pk-runner safe lbl)))

(defn run-db-test-harness [db-conf]
  (apiutils/with-db-data [db-conf]
                         (fn [{:keys [group app-key dbs]}]
                           (run-db-test db-conf group app-key (first dbs)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;; Tests

(deftest pk-cli-db-test
  (doseq [db-record @DATABASES]
    (run-db-test-harness db-record)))