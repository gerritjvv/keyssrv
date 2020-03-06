(ns keyssrv.config
  (:require [cprop.core :refer [load-config]]
            [cprop.source :as source]
            [mount.core :refer [args defstate]]
            [clojure.string :as s]
            [clojure.edn :as edn]
            [mount.core :as mount])
  (:import (org.apache.commons.lang3 StringUtils)))


(defn k->path [k dash level]
  (as-> k $
        (s/lower-case $)
        (s/split $ level)
        (map (comp keyword
                   #(s/replace % dash "-"))
             $)))


(defn str->value [v]
  "ENV vars and system properties are strings. str->value will convert:
  the numbers to longs, the alphanumeric values to strings, and will use Clojure reader for the rest
  in case reader can't read OR it reads a symbol, the value will be returned as is (a string)"
  (cond
    (re-matches #"[0-9]+" v) (Long/parseLong v)
    (re-matches #"^(true|false)$" v) (Boolean/parseBoolean v)
    (re-matches #"\w+" v) v
    :else
    (try
      (let [parsed (edn/read-string v)]
        (if (symbol? parsed)
          v
          parsed))
      (catch Throwable _
        v))))

;; OS level ENV vars
(defn env->path [k]
  (k->path k "_" #"__"))

(defn read-system-env []
  (map (fn [[k v]] [(env->path k)
                    (str->value v)])
       (System/getenv)))


(defn swap-as-per-environment
  " env==prod {:prod-db-url 123} -> {:prod-db-url 123 db-url 123} "
  [env config]
  (if env
    (let [env' (name env)]
      (reduce-kv (fn [m k v]
                  (let [k' (name k)]
                    (if (s/starts-with? k' env')
                      (assoc m (keyword (StringUtils/removeStart (str k') (str env' "-"))) v)
                      m)))
                 config
                 config))
    config))

;;;;;
;;;; Config we can use PROD_DB_URL and so on to provide environment values
(defstate env
  :start
  (let [config (load-config
                 :merge
                 [(args)
                  (source/from-system-props)
                  (source/from-env)])]
    (swap-as-per-environment (:env config) config)))



(defn bitbucket? []
  ;; depends on env, we must ensure its started
  true)