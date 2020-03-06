(ns keyssrv.test.secret.argson2-test
  (:require [clojure.test :refer :all]
            [keyssrv.secret.argon2 :as argon2]))


(deftest hash-and-verify-test
  (let [pass "bla"
        {:keys [pass-hash pass-info]} (argon2/create-hash {:salt (.getBytes "hithisisatestsalt")} pass)
        verified (argon2/verify-password pass-info pass-hash pass)]

    (is
      (= :argon2id (:hash-type pass-info)))

    (is
      (not-empty (:salt pass-info)))

    (is verified)))
