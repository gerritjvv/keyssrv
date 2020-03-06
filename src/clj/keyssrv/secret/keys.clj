(ns
  ^{:doc "

  Handle all keys and encryption related tasks

  GLOBAL_KEYS is a mount component that contains the RSA keys as a (instanceof? KeyPair (:key-pair GLOBAL_KEYS))

  "}
  keyssrv.secret.keys
  (:require
    [clojure.java.io :as io]
    [keyssrv.config :as config]
    [keyssrv.utils :as utils]
    [keyssrv.config :as config]
    [one-time.core :as ot]
    [clojure.tools.logging :refer [error info]]
    [mount.core :as mount]
    [clj-uuid :as clj-uuid]
    [juxt.dirwatch :as djuxt]
    [keyssrv.secret.crypto :as kcrypto]
    [keyssrv.secret.passwords :as passwords]
    [clojure.tools.logging :as log])
  (:import (org.apache.commons.lang3 StringUtils)
           (java.security KeyStore)
           (keyssrv.util CryptoHelper)
           (java.util List)
           (java.io File)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; private functions

(defn as-files ^List [path]
  (map io/file (StringUtils/split (str path) \,)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; public functions

(mount/defstate SYSTEM_KEY
                :start (do
                         (mount/start #'config/env)
                         (when-not (:system-key config/env)
                           (throw (RuntimeException. (str ":system-key must be defined"))))

                         {:system-key (:system-key config/env)}))

(defn load-keystore ^KeyStore [keystore-file pwd]
  (prn "Loading keystore: " keystore-file)
  (KeyStore/getInstance (clojure.java.io/file keystore-file) (.toCharArray (str pwd))))

(defn add-reload-watcher
  "Detect directory changes and call every function with the file in notify-fns
   (doseq [f @notify-fns-a] (f file))"

  [^File keystore-file notify-fns-a]
  (djuxt/watch-dir (fn [_]
                     (try
                       (doseq [f @notify-fns-a]
                         (f keystore-file))
                       (catch Exception e (error e))))
                   (.getParentFile keystore-file)))

;(mount/defstate GLOBAL_KEYS
;                :start (do
;
;                         (mount/start #'config/env)
;
;                         (let [
;                               ^File keystore-file (clojure.java.io/file (:keystore-path config/env))
;
;                               _ (when-not (or keystore-file (.exists keystore-file))
;                                   (throw (RuntimeException. (str "The env variable KEYSTORE_PATH must be defined and point to a valid p12 keystore file"))))
;
;                               keystore-pwd (:keystore-pwd config/env)
;                               keystore-a (atom (load-keystore keystore-file keystore-pwd))
;
;                               ;;on change reload the keystore
;                               reload-global-keys (fn [_]
;                                                    (swap! keystore-a (fn [_] (load-keystore keystore-file keystore-pwd))))
;
;
;                               ;;on any change call the reload-global-keys function
;                               notify-fns-a (atom [reload-global-keys])
;                               loader-fn (add-reload-watcher keystore-file notify-fns-a)]
;
;
;
;
;                           {:notify-fns-a notify-fns-a
;                            :loader-fn    loader-fn
;                            :keystore-pwd keystore-pwd
;                            :key-store-a    keystore-a}))
;
;                :stop
;                (when-let [f (:loader-fn GLOBAL_KEYS)]
;                  (djuxt/close-watcher f)))

;
;(defn add-key-change-listener
;  "f will be called when the directory that :keystore-path is in changes like (f keystore-file)"
;  [f]
;  (swap! (:notify-fns-a GLOBAL_KEYS) conj f))
;
;(defn keystore-pwd ^String []
;  (:keystore-pwd GLOBAL_KEYS))
;
;(defn keystore ^KeyStore []
;  @(:key-store-a GLOBAL_KEYS))


(defn api-key-hash
  "Derive a hash from the password"
  [_ password]
  (:pass-hash (passwords/derive-pass-hash {:hash-type :bcrypt} password)))

(defn password-hash
  "Derive a hash from the password"
  [pass-info password]
  (passwords/derive-pass-hash pass-info password))

(defn check-api-key-against-hash
  "Check the password against its hash
   any error is printed to log/debug and nil is returned.
   Security: We do not want to throw exceptions through to the ui/cli on login checks"
  [_ password hash]
  {:pre [password hash]}
  (try
    (passwords/verify-pass-hash {:hash-type :bcrypt} hash password)
    (catch Exception e
      (log/error e)
      nil)))

(defn check-against-hash
  "Check the password against its hash
   any error is printed to log/debug and nil is returned.
   Security: We do not want to throw exceptions through to the ui/cli on login checks"
  [pass-info pass-hash password]
  {:pre [pass-hash password]}
  (when (empty? pass-hash)
    (throw (RuntimeException. (str "pass hash cannot be null here"))))
  (try
    (passwords/verify-pass-hash pass-info pass-hash password)
    (catch Exception e
      (log/error e)
      nil)))

(defn decrypt-with-pass ^"[B" [pass encrypted]
  {:pre [pass encrypted]}
  (kcrypto/decrypt-with-pass (utils/ensure-bytes pass) (utils/ensure-bytes encrypted)))


(defn decrypt-v1 ^"[B" [key encrypted]
  {:pre [key encrypted]}
  (kcrypto/decrypt-v1 (utils/ensure-bytes key) (utils/ensure-bytes encrypted)))


(defn decrypt-v2 ^"[B" [key encrypted]
  {:pre [key encrypted]}
  (kcrypto/decrypt-v2 (utils/ensure-bytes key) (utils/ensure-bytes encrypted)))


(defn decrypt ^"[B" [key encrypted]
  {:pre [key encrypted]}
  (kcrypto/decrypt (utils/ensure-bytes key) (utils/ensure-bytes encrypted)))

(defn encrypt [key text]
  {:pre [key text]}
  (kcrypto/encrypt (utils/ensure-bytes key) (utils/ensure-bytes text)))

(defn encrypt-with-pass [pass text]
  {:pre [pass text]}
  (kcrypto/encrypt-with-pass (utils/ensure-bytes pass) (utils/ensure-bytes text)))

(defn ^"[B" gen-key []
  (CryptoHelper/genKey))

(defn ^String gen-readable-key []
  (str (clj-uuid/v4)))

(defn ^String gen-verification-key []
  (str (ot/get-totp-token (ot/generate-secret-key))))

(defn ^"String" system-key []
  (let [k (:system-key SYSTEM_KEY)]
    (when-not k
      (throw (RuntimeException. (str "SYSTEM_KEY must be initialised with mount/start first"))))
    k))