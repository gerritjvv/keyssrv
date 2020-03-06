(ns keyssrv.test.secret.passwords-test
  (:require [clojure.test :refer :all]
            [keyssrv.secret.passwords :as passwords])
  (:import (keyssrv.util CryptoHelper)))


(deftest test-password-default-hash
  (let [pass "bla"
        salt (CryptoHelper/genKey 16)
        {:keys [pass-info pass-hash]} (time (passwords/derive-pass-hash {:salt salt} pass))
        verified (time (passwords/verify-pass-hash pass-info pass-hash pass))]

    (is
      (= :argon2id (:hash-type pass-info)))

    (is
      (not-empty (:salt pass-info)))

    (is verified)))

(deftest test-password-argon2-hash
  (let [pass "bla"
        salt (CryptoHelper/genKey 16)
        {:keys [pass-info pass-hash]} (passwords/derive-pass-hash {:pass-type :argon2id :salt salt} pass)
        verified (passwords/verify-pass-hash pass-info pass-hash pass)]

    (is
      (= :argon2id (:hash-type pass-info)))

    (is
      (not-empty (:salt pass-info)))

    (is verified)))