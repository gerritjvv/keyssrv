(ns keyssrv.secret.passwords
  (:require [keyssrv.utils :as utils]
            [keyssrv.config :as config]
            [keyssrv.secret.argon2 :as argon2]
            [clojure.tools.logging :as log]
            [buddy.hashers :as hashers])
  (:import (keyssrv.util Utils)))


(defmulti derive-pass-hash (fn [password-record password] (:hash-type password-record)))

(defmulti verify-pass-hash (fn [password-record pass-hash password] (:hash-type password-record)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; old legacy bcrypt functions

(defn -hash-alg []
  (let [salt (str (:salt config/env))]
    (when-not salt
      (throw (RuntimeException. (str ":salt is not defined in config/env, please make sure that config is initialised with mount/start"))))
    (try
      {:alg  :bcrypt+blake2b-512
       :salt (Utils/as128Bits salt)}
      (catch Exception e
        (throw (RuntimeException. (str "Error when using the (:salt config/env) value = " salt ", must be defined and at least 16 chars") e))))))

(defn -bcrypt-password-hash
  "Derive a hash from the password"
  [password]
  (hashers/derive (utils/ensure-str password) (-hash-alg)))

(defn -password-hash-from-bytes ^String [v]
  (if (bytes? v)
    (String. ^"[B" v "UTF-8")
    (str v)))

(defn -brcrypt-verify
  "Check the password against its hash
   any error is printed to log/debug and nil is returned.
   Security: We do not want to throw exceptions through to the ui/cli on login checks"
  [password hash]
  {:pre [password hash]}
  (try
    (hashers/check (utils/ensure-str password) (-password-hash-from-bytes hash)
                   (-hash-alg))
    (catch Exception e
      (log/debug e)
      nil)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; Public functions
;; returns {:pass-hash <hash> :pass-info {:hash-type :memory ....}}
(defmethod derive-pass-hash :bcrypt [_ password]
          {:pass-hash (-bcrypt-password-hash password)
           :pass-info {:hash-type :bcrypt}})

;; return true/false
(defmethod verify-pass-hash :bcrypt [_ pass-hash password]
          (-brcrypt-verify password pass-hash))


;; returns {:pass-hash <hash> :pass-info {:hash-type :memory ....}}
(defmethod derive-pass-hash :argon2id [hash-info password]
          (argon2/create-hash hash-info password))

;; returns true/false
(defmethod verify-pass-hash :argon2id [hash-info pass-hash password]
  (when (empty? pass-hash)
    (throw (RuntimeException. (str "pass-hash cannot be nil here"))))

          (try
            (argon2/verify-password hash-info pass-hash password)
            (catch Exception e
              (log/error e)
              false)))


(defonce DEFAULT-HASH-INFO (assoc
                             argon2/HASHER-INFO
                             :pass-type :argon2id))

(defmethod verify-pass-hash :default [hash-info pass-hash password]
  (verify-pass-hash (assoc hash-info :hash-type :bcrypt) pass-hash password))

;; returns{:pass-hash <hash> :pass-info {:hash-type :memory ....}}
(defmethod derive-pass-hash :default [hash-info password]
  (derive-pass-hash (assoc hash-info :hash-type :argon2id) password))


