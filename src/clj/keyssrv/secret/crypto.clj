(ns keyssrv.secret.crypto
  (:require [keyssrv.utils :as utils]
            [buddy.core.crypto :as crypto]
            [buddy.core.hash :as hash]
            [keyssrv.config :as conf]
            [clojure.tools.logging :as log]
            [buddy.core.nonce :as nonce])
  (:import (crypto AES)
           (keyssrv.util CryptoHelper)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; v1

(defonce ENCRYPT-ALGO-LEN {:algorithm :aes256-cbc-hmac-sha512})
(defonce IVLEN 16)

(defn decrypt-v1 ^"[B" [key encrypted]
  {:pre [key encrypted]}
  (let [[iv input] (utils/split-bytes (utils/ensure-bytes encrypted) IVLEN)]


    (crypto/decrypt
      input (hash/sha512 key) iv ENCRYPT-ALGO-LEN)))

(defn encrypt-1 ^"[B" [key text]
  {:pre [key text]}
  (let [iv (nonce/random-bytes IVLEN)
        encrypted (crypto/encrypt (utils/ensure-bytes text) (hash/sha512 key) iv ENCRYPT-ALGO-LEN)]
    (utils/join-bts-array iv encrypted)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; v2

(defn encrypt-v2 [^"[B" k ^"[B" v]
  ;; we create a enc+auth key from the 64 user encryption key
  (AES/encryptCBC (CryptoHelper/createKey k) v))

(defn decrypt-v2 [^"[B" k ^"[B" v]
  (try
    (AES/decryptCBC (CryptoHelper/createKey k) v)
    (catch Exception e
      (log/debug e (str "Error while decrypting v2 " e " trying v1"))
      (decrypt-v1 k v))))


(defn -extract-pass ^"[B" [pass]
  (let [salt (:salt conf/env)]
    (when-not salt
      (throw (RuntimeException. (str ":salt cannot be nil"))))

    (CryptoHelper/extractKey (utils/ensure-bytes salt)
                             (utils/ensure-bytes pass))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;


(defn encrypt [k v]
  (encrypt-v2 k v))

(defn decrypt  [k v]
  (if (CryptoHelper/oldGenEnc (utils/ensure-bytes v) )
    (decrypt-v1 k v)
    (decrypt-v2 k v)))

(defn encrypt-with-pass [k v]
  (encrypt-v2 (-extract-pass k) v))

(defn decrypt-with-pass [pass v]
  (decrypt-v2 (-extract-pass pass) v))


