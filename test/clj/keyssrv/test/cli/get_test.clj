(ns keyssrv.test.cli.get-test
  (:require [clojure.test :refer :all]
            [keyssrv.test.cli.utils :as utils]
            [keyssrv.test.pkcli :as pkcli]
            [camel-snake-kebab.core :as snake]
            [camel-snake-kebab.extras :refer [transform-keys]]
            [keyssrv.test.api.utils :as apiutils])
  (:import (org.apache.commons.lang3 StringUtils)
           (java.util List)))

(defn setup [f]
  (utils/setup f))

(use-fixtures :once setup)


(defn find-by
  [pred json-records]
  (first
    (filter pred json-records)))

(deftest test-get-secrets
  (apiutils/with-secret-data
    (fn [{:keys [group app-key lbl secret]}]
      (let [{:keys [key-id key-secret]} app-key

            lines (pkcli/run {:PK-KEY-ID     key-id
                              :PK-KEY-SECRET key-secret}
                             "get"
                             "secret"
                             "--safe"
                             (:name group)
                             "--lbls"
                             lbl)

            output (last lines)
            json-output (first (cheshire.core/parse-string output))
            ]

        (is
          (= lbl (get json-output "lbl")))
        (is
          (= secret (get json-output "val")))))))

(deftest test-get-envs
  (apiutils/with-env-data
    (fn [{:keys [group app-key envs]}]
      (let [{:keys [key-id key-secret]} app-key

            lines (pkcli/run {:PK-KEY-ID     key-id
                              :PK-KEY-SECRET key-secret}
                             "get"
                             "env"
                             "--safe"
                             (:name group)
                             "--lbls"
                             (StringUtils/join ^List (mapv :lbl envs) \,))

            output (last lines)
            json-output (cheshire.core/parse-string output)]


        (doseq [env envs]
          (prn "Check " {:env env})
          (let [env-record (find-by #(= (get % "lbl") (:lbl env)) json-output)]
            (is env-record)
            (is
              (= (:val env) (get env-record "val")))))))))

(deftest test-get-certs
  (apiutils/with-cert-data
    (fn [{:keys [group app-key pub-key priv-key lbl]}]
      (let [{:keys [key-id key-secret]} app-key

            lines (pkcli/run {:PK-KEY-ID     key-id
                              :PK-KEY-SECRET key-secret}
                             "get"
                             "cert"
                             "--safe"
                             (:name group)
                             "--lbls"
                             lbl)

            output (last lines)
            json-output (first (cheshire.core/parse-string output))]


        (is
          (= lbl (get json-output "lbl")))
        (is
          (= pub-key (get json-output "pub-key")))
        (is
          (= priv-key (get json-output "priv-key")))))))


(deftest test-get-snippets
  (apiutils/with-snippet-data
    (fn [{:keys [group app-key snippets]}]
      (let [{:keys [key-id key-secret]} app-key

            lines (pkcli/run {:PK-KEY-ID     key-id
                              :PK-KEY-SECRET key-secret}
                             "get"
                             "note"
                             "--safe"
                             (:name group)
                             "--lbls"
                             (StringUtils/join ^List (mapv :title snippets) \,))

            output (last lines)
            json-output (cheshire.core/parse-string output)]


        (doseq [s snippets]
          (let [s-record (find-by #(= (get % "title") (:title s)) json-output)]
            (is s-record)
            (is
              (= (:val s) (get s-record "val")))))))))


(defn logins-test-helper [assert-f flags]
  (apiutils/with-login-data
    (fn [{:keys [group app-key logins]}]
      (let [{:keys [key-id key-secret]} app-key

            lines (apply pkcli/run {:PK-KEY-ID     key-id
                                    :PK-KEY-SECRET key-secret}
                         "get"
                         "login"
                         "--safe"
                         (:name group)
                         "--lbls"
                         (StringUtils/join ^List (mapv :lbl logins) \,)
                         flags)]



        (assert-f logins lines)))))

(defn logins-test-helper-login [assert-f flags]
  (apiutils/with-login-data
    (fn [{:keys [group app-key logins]}]
      (let [{:keys [key-id key-secret]} app-key

            lines (apply pkcli/run {:PK-KEY-ID     key-id
                                    :PK-KEY-SECRET key-secret}
                         "get"
                         "login"
                         "--safe"
                         (:name group)
                         "--logins"
                         (StringUtils/join ^List (mapv :login logins) \,)
                         flags)]



        (assert-f logins lines)))))


;;use the --lbls flag
(deftest test-get-logins
  (logins-test-helper
    (fn [logins lines]
      (let [output (last lines)
            json-output (cheshire.core/parse-string output)]

        (doseq [s logins]
          (let [s-record (find-by #(= (get % "login") (:login s)) json-output)]
            (is s-record)

            (is (= (:lbl s) (get s-record "lbl")))
            (is (= (:login s) (get s-record "login")))
            (is (= (:user-name s) (get s-record "user-name")))
            (is (= (:user-name2 s) (get s-record "user-name2")))
            (is (= (:secret s) (get s-record "secret")))))))
    ""))

;;use the --logins flag
(deftest test-get-logins-login
  (logins-test-helper-login
    (fn [logins lines]
      (let [output (last lines)
            json-output (cheshire.core/parse-string output)]

        (doseq [s logins]
          (let [s-record (find-by #(= (get % "login") (:login s)) json-output)]
            (is s-record)

            (is (= (:lbl s) (get s-record "lbl")))
            (is (= (:login s) (get s-record "login")))
            (is (= (:user-name s) (get s-record "user-name")))
            (is (= (:user-name2 s) (get s-record "user-name2")))
            (is (= (:secret s) (get s-record "secret")))))))
    ""))

(deftest test-get-logins-jq
  (logins-test-helper
    (fn [logins lines]
      (let [output (last lines)]
        (is
          (seq (find-by #(= (:user-name %) output) logins)))))
    ["--jq" ".[0].user-name"]))


(defn update-map-keys [m]
  )
(deftest test-get-dbs
  (apiutils/with-db-data
    (fn [{:keys [group app-key dbs]}]
      (let [{:keys [key-id key-secret]} app-key

            lines (pkcli/run {:PK-KEY-ID     key-id
                              :PK-KEY-SECRET key-secret}
                             "get"
                             "db"
                             "--safe"
                             (:name group)
                             "--lbls"
                             (StringUtils/join ^List (mapv :lbl dbs) \,))

            output (last lines)
            json-output (cheshire.core/parse-string output)]


        (prn json-output)
        (doseq [s dbs]
          (let [s-record (find-by #(= (get % "lbl") (:lbl s)) json-output)]
            (is s-record)
            (prn {:s s
                  :s-record (transform-keys snake/->kebab-case-keyword s-record)})
            (is
              (= s (transform-keys snake/->kebab-case-keyword s-record)))))))))
