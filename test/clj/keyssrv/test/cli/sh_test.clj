(ns
  ^{:doc "

  Test the pk cli command
    pk env --safe=<safeid> --lbls=myenv1 echo $MYVAR
  "}

  keyssrv.test.cli.sh-test
  (:require [clojure.test :refer :all]
            [keyssrv.test.cli.utils :as utils]
            [keyssrv.test.pkcli :as pkcli]
            [keyssrv.test.api.utils :as apiutils]
            [again.core :as again])
  (:import (org.apache.commons.lang3 StringUtils)
           (java.util List Base64)))

(defn setup [f]
  (utils/setup f))

(use-fixtures :once setup)

(defn with-retries [f]
  (again/with-retries
    {::again/callback (fn [_] (setup #()))
     ::again/strategy [100 1000 10000]
     ::again/user-context (atom {})}
    (f)))

(defn encode [to-encode]
  (.encodeToString (Base64/getEncoder) (.getBytes (str to-encode))))


(defn contains-str [s lines]
  (some #(StringUtils/contains (StringUtils/lowerCase (str %)) (StringUtils/lowerCase (str s))) lines))

(deftest invoke-echo-with-mounts
  (again/with-retries
    {::again/callback (fn [& _] (setup #()))
     ::again/strategy [100 1000 10000]
     ::again/user-context (atom {})}
    (apiutils/with-env-data
      "
      MY_VAR=BLATESTDATA
      MY_VAR2= <<EOF
      BLA1
      BLA2
      BLA3
      EOF
      MY_VAR3=3
      "
      (fn [{:keys [group app-key envs]}]
        (let [{:keys [key-id key-secret]} app-key
              env (first envs)

              lines (pkcli/run {:PK-KEY-ID     key-id
                                :PK-KEY-SECRET key-secret}
                               "sh"
                               "-s"
                               (:name group)
                               "-v"
                               (:lbl env)
                               "--"
                               "cat"
                               (str "$F_" (StringUtils/replace (str (:lbl env)) "-" "_")))

              retStr (apply str lines)]

          (is
            (StringUtils/contains (str retStr) "MY_VAR=BLATESTDATA")))))))


(deftest test-invoke-echo-with-mounts
  (with-retries invoke-echo-with-mounts))

(defn invoke-echo-with-env []
  (apiutils/with-env-data
    "
    MY_VAR=BLATESTDATA
    MY_VAR2= <<EOF
    BLA1
    BLA2
    BLA3
    EOF
    MY_VAR3=3
    "
    (fn [{:keys [group app-key envs]}]
      (let [{:keys [key-id key-secret]} app-key

            lines (pkcli/run {:PK-KEY-ID     key-id
                              :PK-KEY-SECRET key-secret}
                             "sh"
                             "-s"
                             (:name group)
                             "-n"
                             (StringUtils/join ^List (mapv :lbl envs) \,)
                             "--"
                             "echo"
                             "$MY_VAR3")

            extract-numbers #(str (first (re-find #"\d+" (str %))))

            output (filter
                     #(StringUtils/isNumeric (str %))
                     (map extract-numbers lines))]

        (prn "Testing with output : " {:lines lines
                                       :output output})


        (is
          (= 3 (Integer/parseInt (first output))))))))


(deftest test-invoke-echo-with-env
  (with-retries
    invoke-echo-with-env))

(deftest test-wrong-keys
  (let [[err-code lines] (pkcli/run-no-exception {:PK-KEY-ID     "BLA1"
                                                  :PK-KEY-SECRET "BLA1"}
                                                 "sh"
                                                 "--safe"
                                                 "bla"
                                                 "--lbls"
                                                 "bla123"
                                                 "--"
                                                 "echo"
                                                 "$MY_VAR3")]

    (is (not (zero? err-code)))

    (is (contains-str "Authentication information provided is not valid" lines))))

(deftest test-invoke-no-env
  (apiutils/with-env-data
    "
    MY_VAR=BLATESTDATA
    "
    (fn [{:keys [group app-key]}]
      (let [{:keys [key-id key-secret]} app-key

            [err-code lines] (pkcli/run-no-exception {:PK-KEY-ID     key-id
                                                      :PK-KEY-SECRET key-secret}
                                                     "sh"
                                                     "--safe"
                                                     (:name group)
                                                     "--lbls"
                                                     "bla123"
                                                     "--"
                                                     "echo"
                                                     "$MY_VAR3")]


        (is
          (not (zero? err-code)))

        (is
          (contains-str "No environment" lines))))))


(deftest test-invoke-no-safe
  (apiutils/with-env-data
    "
    MY_VAR=BLATESTDATA
    "
    (fn [{:keys [app-key]}]
      (let [{:keys [key-id key-secret]} app-key

            [err-code lines] (pkcli/run-no-exception {:PK-KEY-ID     key-id
                                                      :PK-KEY-SECRET key-secret}
                                                     "sh"
                                                     "--safe"
                                                     "bla"
                                                     "--lbls"
                                                     "bla123"
                                                     "--"
                                                     "echo"
                                                     "$MY_VAR3")]


        (is
          (contains-str "not found" lines))
        (is
          (not (zero? err-code)))

        ))))