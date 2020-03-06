(ns keyssrv.pwd.checks
  (:require [mount.core :as mount]
            [clojure.java.io :as io]))



(defn load-common-pwds []
      (set
        (line-seq
          (io/reader
            (io/input-stream (io/resource "pwds/1000-common-pwds.txt"))))))

(mount/defstate COMMON-PWDS
                :start (load-common-pwds))



(defn is-common-pwd? [pwd]
  (COMMON-PWDS pwd))
