(ns
  ^{:doc "User registration service logic, routing and web logic goes into routes/users.clj

  On register:
     send-user-registration
  On registration confirm
     do-register-confirm

  @TODO complete send-email
  "}
  keyssrv.users.registration
  (:require [keyssrv.secret.keys :as keys]
            [keyssrv.db.core :as db]
            [camel-snake-kebab.core :as ccore]
            [camel-snake-kebab.extras :refer [transform-keys]]
            [clj-uuid]
            [keyssrv.utils :as utils]
            [clojure.tools.logging :as log]
            [keyssrv.secret.argon2 :as argon2])
  (:import (org.apache.commons.lang3 StringUtils)
           (keyssrv.util CryptoHelper)))


(defn get-user-by-email [email]
  (when email
    (let [v (db/get-user-by-email {:email (utils/ensure-user-email email)})]
      (when (not-empty v)
        v))))

(defn get-user-by-user-name [user-name]
  (when user-name
    (let [v (db/get-user-by-user-name {:user-name (utils/ensure-user-name user-name)})]
      (when (not-empty v)
        v))))

(defn get-user-by-user-id [id]
  (let [v (db/get-user-by-id {:id id})]
    (when (not-empty v)
      v)))

(defn get-user-by-user-gid [gid]
  (let [v (db/get-user-by-gid {:gid gid})]
    (when (not-empty v)
      v)))

(defn get-user-by-user-name' [{:keys [user-name]}]
  (let [user (get-user-by-user-name user-name)]
    (when (and
            (:email user)
            (:pass-hash user))
      user)))

(defn get-user-by-email' [{:keys [email]}]
  (let [user (get-user-by-email email)]
    (when (and
            (:email user)
            (:pass-hash user))
      user)))


(defn decrypt-pass-hash
  "Takes {:pass-hash-enc :pass-hash} and decrypts it
  Return returns {:pass-hash decrypt(pass-hash-enc)}"
  [{:keys [pass-hash pass-hash-enc] :as db-record}]
  (if pass-hash-enc
    (try
      (assoc db-record :pass-hash (keys/decrypt-v2 pass-hash pass-hash-enc))
      (catch Exception e
        (log/error e)
        (log/debug e)
        ;; we never want to throw an exception here
        (assoc db-record :pass-hash (byte-array 0))))
    db-record))

(defn encrypt-pass-hash
  "Takes {:pass-hash} return returns {:pass-hash encrypted}"
  [{:keys [pass-hash] :as db-record}]
  (assoc db-record :pass-hash (utils/ensure-bytes (keys/encrypt pass-hash pass-hash))))

(defn encrypt-key
  "Encrypts the enc-key with the pass-hash"
  [{:keys [pass-hash enc-key] :as r}]
  (assoc
    r
    :enc-key (keys/encrypt pass-hash enc-key)))

(defn update-user-pass-and-enc
  "Encrypts the pass-hash before storing"
  [user pass-info password-hash enc-key]

  (when (empty? pass-info)
    (throw (RuntimeException. (str "Passinfo cannot be empty here"))))

  (db/update-user-pass-enc!
    (encrypt-pass-hash
      (encrypt-key
        {:id        (:id user)
         :pass-info (utils/encode pass-info)
         :pass-hash (utils/ensure-bytes password-hash)
         :enc-key   (utils/ensure-bytes enc-key)}))))


(defn update-mfa-code [user mfa-key-enc]
  (db/update-user-mfa-enc!
    {:id          (:id user)
     :mfa-key-enc (utils/ensure-bytes mfa-key-enc)}))

(defn create-user
  ([user-name email password-hash enc-key pass-info]
   (create-user user-name email password-hash enc-key pass-info 0 0))

  ([user-name email password-hash enc-key pass-info wizz-i step-i]

   (when (empty? pass-info)
     (throw (RuntimeException. (str "pass-info cannot be nil here"))))

   (transform-keys ccore/->kebab-case-keyword
                   (db/create-user!
                     {:email       (utils/ensure-user-email email)
                      :user-name   (utils/ensure-user-name user-name)
                      ;; this is stored encrypted, because we use it as an encryption key for the enc_key
                      :pass-hash   (utils/ensure-bytes password-hash)
                      :pass-info   (utils/encode (dissoc pass-info :pass-hash)) ;; we cannot save the pass-hash here
                      :confirmed   false
                      :org         nil
                      :mfa-key-enc nil
                      :enc-key     (utils/ensure-bytes enc-key) ;;the user's enc key encryption with the user's pass
                      :wiz         wizz-i
                      :wiz-step    step-i
                      }))))

(defn create-registered-user
  "Throws an exception if the user already exists
   Otherwise saves the user to the database and return the user

   plan and plan-period are only a hint here, for the actual user plan please select the user_plans
   mainly used for the reg wizzard data.
   "
  [{:keys [email user-name password pass-info wizz-i step-i]}]
  {:pre [email user-name password]}
  (let [{:keys [pass-info pass-hash]} (keys/password-hash (assoc
                                                            pass-info
                                                            :salt (CryptoHelper/genKey argon2/SALT_LENGTH)) password)

        ;; security note: the password hash is never stored un-encrypted (i.e its stored encrypted by itself)
        ;;  so is safe to use as encryption key here. this allows us to use the strong derived password hash
        ;;   as an ecnryption key, from which an authentication and encryption key is created using HKDF
        enc-key (keys/encrypt pass-hash (keys/gen-key))     ;;the user encryption key, encrypted with the user's password
        user-name' (StringUtils/lowerCase (str (or user-name email)))]

    (when (empty? pass-info)
      (throw (RuntimeException. "pass-info cannot be empty here")))

    (let [user (db/with-transaction (fn [_]
                                      (when (seq (get-user-by-email email))
                                        (throw (RuntimeException. (str "User already exists"))))

                                      (when (seq (get-user-by-user-name user-name'))
                                        (throw (RuntimeException. (str "User already exists"))))


                                      (create-user user-name'
                                                   email
                                                   (keys/encrypt pass-hash pass-hash)
                                                   enc-key
                                                   pass-info
                                                   wizz-i
                                                   step-i)))]
      user)))

(defn set-user-email-confirmed [user]
  {:pre [(:id user)]}
  (db/update-user-email-confirmed! {:id        (:id user)
                                    :confirmed true}))

(defn update-wizzard-db-data [user wizz-i step-i]
  {:pre [(:id user) (number? wizz-i) (number? step-i)]}
  (db/update-user-wizz! {:id       (:id user)
                         :wiz      wizz-i
                         :wiz-step step-i}))

(defn validate-password-hash [{:keys [pass-hash pass-info]} password]
  {:pre [pass-hash]}
  (when (empty? pass-hash)
    (throw (RuntimeException. (str "pass hash cannot be nil here"))))

  (when-not (keys/check-against-hash pass-info pass-hash password)
    (prn "validate-password-hash:  " {:r pass-hash
                                      ;:pass-info pass-info
                                      ;:salt (into [] (:salt pass-info))
                                      ;:password (utils/ensure-str password)
                                      ;
                                      })
    {:password-error "Incorrect login details"}))

(defn -check-for-brypt!
  "If the user has a bcrypt encoded password, we rehash with argon2
   Its important that the hash is checked against the password before calling this method.
   see validate-user-login
   "
  [{:keys [pass-hash id] :as r} password]

  ;(when (CryptoHelper/isBcryptHash (utils/ensure-bytes pass-hash))
  ;  (log/info "Rehashing users bcrypt hash to argon2 id " id)
  ;  (let [enc-key (:enc-key r)
  ;        {:keys [pass-hash pass-info]} (passwords/derive-pass-hash {:salt (CryptoHelper/genKey 16)} password)]
  ;
  ;    (when (empty? pass-hash)
  ;      (throw (RuntimeException. (str "The derive pass must return a pass-hash"))))
  ;    (when (or (empty? pass-info)
  ;              (empty? (:salt pass-info)))
  ;      (throw (RuntimeException. (str "The derive pass must return a pass-info"))))
  ;
  ;    (db/update-user-pass-enc!
  ;      {:id        id
  ;       :enc-key   (utils/ensure-bytes (crypto/encrypt-v2
  ;                                        (utils/ensure-bytes pass-hash) (utils/ensure-bytes enc-key)))
  ;
  ;       :pass-hash (utils/ensure-bytes (keys/encrypt pass-hash pass-hash))
  ;       :pass-info (utils/encode (dissoc pass-info :pass-hash))}))
  ;
  ;     true)
  ;
  nil
  )

(defn validate-user-login
  "Return any true value is an error, normally the validation error "
  [db-user-record params]
  (when (empty? (:pass-hash db-user-record))
    (throw (RuntimeException. (str "pass hash cannot be nil here"))))

  (let [errors (validate-password-hash
                 db-user-record
                 (:password params))]

    errors))


(defn decode-pass-info [user]
  (if (:pass-info user)
    (update user :pass-info #(utils/decode (utils/ensure-bytes %)))
    user))

(defn maybe-decrypt-pass-hash [hash-type pass-hash db-user-record]
  (let [db-record (assoc
                    db-user-record
                    :pass-hash pass-hash                    ;; the password hash
                    :pass-hash-enc (:pass-hash db-user-record) ;;the same hash encrypted
                    )]

    (when (empty? pass-hash)
      (throw (RuntimeException. (str "pass hash cannot be null here"))))

    (if (= hash-type :bcrypt)
      db-record
      (decrypt-pass-hash
        db-record))))

(defn get-user-record-and-login
  "Checks the db for the user by user-id, and then checks the user password against the hash

   return {:errors <str> :db-user-record <user record>}"
  [params]
  {:pre [(:user-name params) (:password params)]}

  (try
    (let [db-user-record (decode-pass-info
                           (or (get-user-by-user-name' params)
                               (get-user-by-email' (assoc params :email
                                                                 (:user-name params)))))

          pass-info (:pass-info db-user-record)

          ;; if no hash-type in pass-info we have the old bcrypt type
          pass-hash-type (if (:hash-type pass-info) :argon2id :bcrypt)

          ;; hash the password to decrypt the hash
          {:keys [pass-hash]} (when (not-empty db-user-record)
                                          (keys/password-hash (assoc (:pass-info db-user-record) :hash-type pass-hash-type)
                                                              (:password params)))

          db-user-record'' (when (not-empty db-user-record)
                             (try
                               ;; we decrypt the pass-hash, but if not encrypted ( v1 ) we leave blank
                               (maybe-decrypt-pass-hash pass-hash-type pass-hash db-user-record)
                               (catch Exception e
                                 (log/debug (str "[Can ignore] Error while decrypting-pass-hash: " e))
                                 db-user-record)))

          ]

      (when (and db-user-record''
                 (empty? (:pass-hash db-user-record'')))
        (throw (RuntimeException. (str "pass-hash cannot be empty here"))))
      ;(-check-for-brypt! db-user-record'' (:password params))

      (if (empty? db-user-record'')
        {:errors "The user is not registered"}
        (if-let [errors (validate-user-login db-user-record'' params)]
          {:errors (first (vals errors))}
          {:db-user-record db-user-record''})))
    (catch Exception e
      (log/error e)
      {:errors (str "Error while generating your password hash, please contact us at admin@pkhub.io. Apologies for the inconvenience.")})))