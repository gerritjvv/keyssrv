(ns
  ^{:doc "

  Use register-hub-sql-hyphenated and expend-pg-data-types to get
    correct clojure column hyphenation and correct clojure postgres data types
  "}
  keyssrv.db.db_utils
  (:require
    [cheshire.core :refer [generate-string parse-string]]
    [clj-time.jdbc]
    [clojure.java.jdbc :as jdbc]
    [clojure.string :as cljstr]
    [mount.core :refer [defstate]]
    [hugsql.adapter :as adapter]
    [fun-utils.cache :as cache]
    [camel-snake-kebab.core :as camel]
    [hugsql.core :as hugsql])

  (:import (org.postgresql.util PGobject)
           java.sql.Array
           (clojure.lang IPersistentMap IPersistentVector)
           [java.sql
            PreparedStatement]))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;; wrapping db adapter to hyphenate keywords
;;;;; i.e instead of my_col we get my-col.
;;;;; this has performance implications,
;;;;; so only use :? :* when not on the critical path.
;;;;; raw (default) are returned as is.


(def memoized->kebab-case
  (cache/memoize camel/->kebab-case :maximum-size 512))

(defn kebab-case-ks [m]
  (reduce-kv #(assoc %1 (memoized->kebab-case %2) %3) {} m))

(defn create-hyphenated-adapter-wrapper [adapter]
  (reify
    adapter/HugsqlAdapter
    (execute [this db sqlvec options]
      (adapter/execute adapter db sqlvec options))

    (query [this db sqlvec options]
      (adapter/query adapter db sqlvec options))

    (result-one [this result options]
      (kebab-case-ks (adapter/result-one adapter result options)))

    (result-many [this result options]
      (map kebab-case-ks (adapter/result-many adapter result options)))

    (result-affected [this result options]
      (first result))

    (result-raw [this result options]
      result)

    (on-exception [this exception]
      (throw exception))))


(defn register-hub-sql-hyphenated
  "Ensure that the column names are returned with clojure style hyphenation"
  []
  (hugsql/set-adapter! (create-hyphenated-adapter-wrapper (hugsql/get-adapter))))


(defn to-pg-json [value]
  (doto (PGobject.)
    (.setType "jsonb")
    (.setValue (generate-string value))))


(defn expend-pg-data-types []
  (extend-protocol jdbc/IResultSetReadColumn
    Array
    (result-set-read-column [v _ _] (vec (.getArray v)))

    PGobject
    (result-set-read-column [pgobj _metadata _index]
      (let [type (.getType pgobj)
            value (.getValue pgobj)]
        (case type
          "json" (parse-string value true)
          "jsonb" (parse-string value true)
          "citext" (str value)
          value))))


  (extend-type IPersistentVector
    jdbc/ISQLParameter
    (set-parameter [v ^PreparedStatement stmt ^long idx]
      (let [conn (.getConnection stmt)
            meta (.getParameterMetaData stmt)
            type-name (.getParameterTypeName meta idx)]
        (if-let [elem-type (when (= (first type-name) \_) (cljstr/join (rest type-name)))]
          (.setObject stmt idx (.createArrayOf conn elem-type (to-array v)))
          (.setObject stmt idx (to-pg-json v))))))

  (extend-protocol jdbc/ISQLValue
    IPersistentMap
    (sql-value [value] (to-pg-json value))
    IPersistentVector
    (sql-value [value] (to-pg-json value))))