(ns

  ^{:doc "

  encryption utility functions for encrypting in tests

  "}

  keyssrv.test.encrypt
  (:require [clojure.test :refer :all]
            [buddy.core.nonce :as nonce]
            [buddy.core.hash :as hash]
            [buddy.core.crypto :as crypto]
            [buddy.core.codecs :as codecs]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;; encrypt utils to test binary data
;;;;;;;;  use BINARY-PLAIN-TEXT, encrypt and decrypt


(def IV (nonce/random-bytes 16))                            ;; 16 bytes random iv
(def KEY (hash/sha256 "mysecret"))                          ;; 32 bytes key

(defn encrypt [txt]
  (crypto/encrypt (codecs/str->bytes txt)
                  KEY IV
                  {:algorithm :aes128-cbc-hmac-sha256}))

(defn decrypt [encrypted]
  (codecs/bytes->str
    (crypto/decrypt encrypted KEY IV {:algorithm :aes128-cbc-hmac-sha256})))

