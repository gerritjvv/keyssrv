(ns
  ^{:doc "

  Reads language files from resources/i8n that are in edn form.
  The file should start with {:<lang-def> ...}
  "}
  keyssrv.i18n
  (:require
    [mount.core :as mount]
    [clojure.edn :as edn])
  (:import (java.io File)))

(defn file-exist [f]
  (.exist (File. (str f))))

(defn
  load-lang-files
  ([]
   (load-lang-files "resources/i8n"))
  ([folder]
   (let [en (edn/read-string (slurp (str folder "/en-gb.edn")))]
     {:dict
      {:en en
       :en-US en
       :en-GB en
       :* en
       }})))

(mount/defstate I8N
               :start (load-lang-files))

(defn i8n-config []
  I8N)