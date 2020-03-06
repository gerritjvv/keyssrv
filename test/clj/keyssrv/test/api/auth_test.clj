(ns
  ^{:doc "Test functions for the api basic auth"}
  keyssrv.test.api.auth-test
  (:require [clojure.test :refer :all]
            [keyssrv.users.auth :as auth])
  (:import (java.util Base64)))


(defn encode-b64 [user pass]
  (.encodeToString (Base64/getEncoder) (.getBytes (str user ":" pass) "UTF-8")))

(deftest parse-basic-auth-test
  (let [user "test"
        pass "testabc"
        [user' pass'] (auth/parse-basic-auth (str "Basic: " (encode-b64 user pass)))]

    (is
      (= user user')
      (= pass pass'))))