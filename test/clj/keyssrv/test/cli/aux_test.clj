(ns
  ^{:doc "Version
          Getting version without a PK key
          Config file reading etc"}
  keyssrv.test.cli.aux-test
  (:require
    [keyssrv.test.pkcli :as pkcli]
    [keyssrv.test.api.utils :as apiutils]
    [clojure.test :refer :all]
    [keyssrv.test.cli.utils :as utils]))



(defn setup [f]
  (utils/setup f))

(use-fixtures :once setup)


(defn write-config-file [key-id key-secret & {:keys [file]}]
  (let [file-name (or file (str "/tmp/mycfg_" (System/nanoTime)))]
    (spit file-name

          (str
            "PK_KEY_ID: " key-id "\n"
            "PK_KEY_SECRET: " key-secret
            )
          )
    file-name))


(deftest test-version
  (let [lines (pkcli/run {}
                         "version")]

    (is
      (re-matches #"^(\d+\.)?(\d+\.)?(\*|\d+)$" (first lines)))))


(deftest test-read-config-from-custom-location
  (apiutils/with-secret-data
    (fn [{:keys [group app-key lbl]}]
      (let [{:keys [key-id key-secret]} app-key
            config-file (write-config-file key-id key-secret)
            ]
        (pkcli/run
          {}
          "--config" config-file
          "get"
          "secret"
          "--safe"
          (:name group)
          "--lbls"
          lbl)
        ))))

(deftest test-read-config-from-default-file
  (apiutils/with-secret-data
    (fn [{:keys [group app-key lbl]}]
      (let [{:keys [key-id key-secret]} app-key
            _ (write-config-file key-id key-secret :file (str (System/getProperty "user.home") "/.pk.yaml"))
            ]
        (pkcli/run
          {}
          "get"
          "secret"
          "--safe"
          (:name group)
          "--lbls"
          lbl)
        ))))