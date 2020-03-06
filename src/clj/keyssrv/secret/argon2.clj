(ns keyssrv.secret.argon2
  (:require [keyssrv.utils :as utils])
  (:import (com.kosprov.jargon2.api Jargon2 Jargon2$Type Jargon2$Verifier Jargon2$Hasher)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;; private functions

(defonce HASH-TYPE :argon2id)
(defonce MIN_MEMORY 65536)
(defonce MIN_PASSES 4)
(defonce MIN_PARALLELISM 2)
(defonce HASH_LENGTH 32)
(defonce SALT_LENGTH 16)

(defn -hasher-info []
  {:memory      MIN_MEMORY
   :passes      MIN_PASSES
   :parallelism MIN_PARALLELISM
   :hash-length HASH_LENGTH})

(defn -create-hasher ^Jargon2$Hasher [{:keys [memory passes parallelism hash-length]}]
  (-> (Jargon2/jargon2Hasher)
      (.type Jargon2$Type/ARGON2id)
      (.memoryCost (int memory))
      (.timeCost (int passes))
      (.parallelism parallelism)
      (.saltLength (int SALT_LENGTH))
      (.hashLength (int hash-length))))


(defonce HASHER-INFO (-hasher-info))

(defn -create-verifier ^Jargon2$Verifier [{:keys [memory passes parallelism]}]
  (-> (Jargon2/jargon2Verifier)
      (.type Jargon2$Type/ARGON2id)
      (.memoryCost (int memory))
      (.timeCost (int passes))
      (.parallelism parallelism)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;; public functions

(defn verify-password [hash-info pass-hash password]
  {:pre [(:salt hash-info)]}

  (-> (-create-verifier hash-info)
      (.salt (utils/ensure-bytes (:salt hash-info)))
      (.password (utils/ensure-bytes password))
      (.hash (utils/ensure-bytes pass-hash))
      .verifyRaw))

(defn create-hash
  "Returns
  {:pass-hash hash
  :pass-info {:memory :passes :parallelism :hash-length :salt :pass-hash :hash-type}}"
  [hash-info password]

  ;; adding explicit check for SALT here, recreating it here proved problematic
  (when-not (:salt hash-info)
    (throw (RuntimeException. (str ":salt cannot be empty here"))))

  (let [salt (:salt hash-info)
        hash-info (-hasher-info)
        hash (-> (-create-hasher hash-info)
                 (.salt (utils/ensure-bytes salt))
                 (.password (utils/ensure-bytes password))
                 .rawHash)]
    {:pass-hash hash
     :pass-info (assoc
                  hash-info
                  :salt salt
                  :hash-type HASH-TYPE)}))