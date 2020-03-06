(ns
  ^{:doc "
  Test the pk db [copy] and pk db commands

  How to use:
    The test pk-cli-db-test goes through each test in the DATABASES def
    and runs all tests against that particular database

  "}
  keyssrv.test.cli.dbs.db-cmds-test
  (:require [clojure.test :refer :all]
            [keyssrv.test.cli.utils :as utils]
            [keyssrv.test.utils :as test-utils]
            [keyssrv.config :as conf]
            [keyssrv.test.pkcli :as pkcli]
            [keyssrv.test.api.utils :as apiutils]
            [clj-time.format :as f]
            [clj-time.core :as t]
            [clojure.string :as string]
            [cheshire.core :as json])
  (:import (org.apache.commons.io FileUtils)
           (java.io Writer)
           (org.apache.commons.lang3 StringUtils)))


(def DATABASES [
                {:hosted-on        "local"
                 :type             "postgres"
                 :host             "localhost"
                 :create-table-sql (fn [table]
                                     (str "CREATE TABLE " table " (id INT, name varchar, status int, start_date timestamp)"))
                 :dbuser           "test"
                 :password         "test"
                 :database         "test"
                 :port             5432}

                {:hosted-on        "local"
                 :type             "mysql"
                 :create-table-sql (fn [table]
                                     (str "CREATE TABLE " table " (id INT, name varchar(255), status int, start_date datetime)"))

                 :host             "localhost"
                 :dbuser           "test"
                 :password         "test"
                 :database         "test"
                 :port             3306}

                ])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;; Test fixtures

(defn setup [f]
  (utils/setup f))

(use-fixtures :once setup)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;; Utils

(def built-in-formatter (f/formatters :basic-date-time))

(defn create-data-file [n]
  (let [file-name (str (FileUtils/getTempDirectoryPath) "/test_copy_data_" (System/nanoTime))
        columns ["id" "name" "status" "start_date"]
        csv-write (fn [^Writer w & data]
                    (.write w (str (string/join "," data) "\n")))]

    (with-open [^Writer w (clojure.java.io/writer file-name)]
      (apply csv-write w columns)
      (dotimes [i n]
        (csv-write w
                   i                                        ;; -- id
                   (test-utils/unique-str)                  ;; -- name
                   (rand-int 100)                           ;; -- status
                   (f/unparse built-in-formatter            ;; -- start_date
                              (t/now)))
        ))

    [file-name columns]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;; Test Data and Data Setup

(defn setup-db-copy-test-table [{:keys [create-table-sql]} pk-runner safe lbl table]
  (pk-runner
    "db"
    "-s" safe
    "-n" lbl
    "-c"
    (create-table-sql table)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;; Test methods

(defn first-numeric ^String [vars]
  (str (first (filter #(StringUtils/isNumeric (str %)) vars))))

(defn test-select-count
  "Called from db-copy-test
  Assert that the table contains data-n number of rows
  "
  [select-fn table data-n]
  (let [lines (select-fn "-C"
                         "-c" (str "SELECT count(*) FROM " table))
        rows-count (StringUtils/trimToNull (first-numeric lines))]

    (is (StringUtils/isNumeric rows-count) "No row count was found from select count query")
    (is (= (Long/parseLong rows-count) (long data-n)) "The table count is different from the expected data loaded")))

(defn test-csv-out-field-sep
  "Called from db-copy-test
  Assert that the table output -C for csv and -F for field separator works
   Default for -F is | so we test -F ,
  "
  [select-fn table columns]
  {:pre [(fn? select-fn) (string? table) (coll? columns)]}

  (let [row-tab (first (take-last 4 (select-fn "-C"
                                               "-c" (str "SELECT * FROM " table " limit 10"))))
        row-comma (first (take-last 4 (select-fn "-C"
                                                 "-F" ","
                                                 "-c" (str "SELECT * FROM " table " limit 10"))))
        compare-column-on-split (fn [row sep]
                                  (= (count columns) (count (StringUtils/split (str row) (str sep)))))]


    (is
      (compare-column-on-split row-tab "|")
      (compare-column-on-split row-comma ","))))

(defn test-json-out [select-fn table columns]
  {:pre [(fn? select-fn) (string? table) (coll? columns)]}

  (let [json-row (last (select-fn "-J"
                                  "-c" (str "SELECT * FROM " table " limit 10")))

        json-record-list (json/parse-string json-row)

        json-record (first json-record-list)
        ]

    (is
      (= (count json-record-list) 10))

    (is (= (set columns)
           (set (keys json-record))))))

(defn test-file-out [select-fn table columns]
  {:pre [(fn? select-fn) (string? table) (coll? columns)]}

  (let [query-file (str (FileUtils/getTempDirectoryPath) "/test_query_data_" (System/nanoTime))
        output-file (str (FileUtils/getTempDirectoryPath) "/test_out_data_" (System/nanoTime))
        _ (spit query-file (str "SELECT * FROM " table " limit 10"))

        ;; test reading the query from a file and writing the result to an output file
        _ (select-fn "-J"
                     "-o" output-file
                     "-f" query-file)

        json-record-list (json/parse-string (slurp output-file))

        json-record (first json-record-list)
        ]

    ;; these checks are the same as for the json tests but we test that the output to file
    ;; is exactly as expected
    (is
      (= (count json-record-list) 10))

    (is (= (set columns)
           (set (keys json-record))))))

(defn db-cli-copy-tests
  "Run the different db copy parameters"
  [db-conf pk-runner safe lbl]
  (let [table (str "test_table_" (System/nanoTime))]
    (setup-db-copy-test-table db-conf pk-runner safe lbl table)

    (let [data-n (max 1000 (rand 100000))
          [data-file columns] (create-data-file data-n)]
      (pk-runner
        "db"
        "copy"
        "-s" safe
        "-n" lbl
        "-t" table
        "-k"
        "-x" "id,name,status,start_date"
        "-f" data-file)


      ;; check with different output params
      (let [select-fn (fn [& args]
                        (apply
                          pk-runner
                          (concat
                            ["db"
                             "-s" safe
                             "-n" lbl
                             ]
                            args)))]

        ;;;; Test select count of copy
        (test-select-count select-fn table data-n)
        ))))

(defn db-cli-tests
  "Test the different db command options"
  [db-conf pk-runner safe lbl]
  (let [table (str "test_table_" (System/nanoTime))]
    (setup-db-copy-test-table db-conf pk-runner safe lbl table)

    (let [data-n (max 1000 (rand 100000))
          [data-file columns] (create-data-file data-n)]
      (pk-runner
        "db"
        "copy"
        "-s" safe
        "-n" lbl
        "-t" table
        "-f" data-file)


      ;; check with different output params
      (let [select-fn (fn [& args]
                        (apply
                          pk-runner
                          (concat
                            ["db"
                             "-s" safe
                             "-n" lbl
                             ]
                            args)))]

        ;;;; Test select count of copy
        (test-select-count select-fn table data-n)
        (test-csv-out-field-sep select-fn table columns)
        (test-json-out select-fn table columns)
        (test-file-out select-fn table columns)
        ;
        ))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;; Test harnesses

(defn run-db-test [db-conf group {:keys [key-id key-secret]} {:keys [lbl]}]
  (let [auth-record {:PK-KEY-ID     key-id
                     :PK-KEY-SECRET key-secret}

        pk-runner (fn [& args]
                    (apply pkcli/run auth-record args))

        safe (:name group)]

    (db-cli-tests db-conf pk-runner safe lbl)
    (db-cli-copy-tests db-conf pk-runner safe lbl)))

(defn run-db-test-harness [db-conf]
  (apiutils/with-db-data [db-conf]
                         (fn [{:keys [group app-key dbs]}]
                           (run-db-test db-conf group app-key (first dbs)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;; Tests

(deftest pk-cli-db-test
  (doseq [db-record DATABASES]
    (run-db-test-harness db-record)))