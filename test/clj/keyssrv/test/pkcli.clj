(ns
  ^{:doc "Build and run tests for the pk cli"}
  keyssrv.test.pkcli
  (:require [keyssrv.test.swagger :as swagger]
            [keyssrv.test.sh :as cmd])
  (:import (java.io File)))

(defn build
  "Export swagger and build the cli"
  []
  (swagger/dump-swagger-json "cli/pk/swagger.json")
  (cmd/sh {} "cli/pk/build.sh" "swagger")
  (cmd/sh {} "cli/pk/build.sh" "build"))

(defn executable? [file]
  (let [^File file (clojure.java.io/as-file file)]
    (and
      (.isFile file)
      (.canExecute file))))

(defn run
  "Return lines if exit value != 0 a RuntimeException is thrown"
  [env-map & args]
  (when-not (executable? "cli/pk/pk")
    (throw (RuntimeException. (str "No cli/pk/pk executable"))))

  (apply cmd/sh env-map "cli/pk/pk" "--insecure" "--url" "https://127.0.0.1:3001" args))


(defn run-no-exception
  "Return [exit-value, lines]"
  [env-map & args]
  (when-not (executable? "cli/pk/pk")
    (throw (RuntimeException. (str "No cli/pk/pk executable"))))

  (apply cmd/sh-no-exception env-map "cli/pk/pk" "--insecure" "--url" "https://127.0.0.1:3001" args))
