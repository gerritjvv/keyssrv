(ns
  ^{:doc "
  Separate out how the user record for the session and the enc_key is decrypted
  Primarily to solve the cyclic deps between UI and API packages
  "}
  keyssrv.routes.user-utils
  (:require [keyssrv.secret.keys :as keys]
            [keyssrv.billing.plans :as billing-plans]
            [keyssrv.users.registration :as user-registration]
            [keyssrv.utils :as utils]))

(defn add-current-plan
  "Query the current plan for the user and update the {:session {:user {:plan }}}"
  [user]
  (when (seq user)

    (let [record (assoc
                   user
                   :plan
                   (billing-plans/get-active-user-sub-plan user))]

      record)))


(defn decrypt-enc-key [db-user-record password]
  (try
    (keys/decrypt (:pass-hash db-user-record) (:enc-key db-user-record))
    (catch Exception _
      (keys/decrypt-v1 password (:enc-key db-user-record)))))

(defn user-record
  "This is called from the UI and the API code to create the
   User record that is used by other code to encrypt and decrypt objects
   For the api code the password is the api secret and the enc-key is the
   user's enc-key encrypted with the api secret"
  [db-user-record {:keys [user-name password]}]
  {:pre [(:email db-user-record) (:plan db-user-record) (:id db-user-record) (:enc-key db-user-record) (= (:user-name db-user-record) user-name)]}

  {:user-name          user-name
   :plan               (:plan db-user-record)
   :email              (:email db-user-record)
   :id                 (:id db-user-record)
   :pass-hash          (utils/ensure-bytes (:pass-hash db-user-record))
   :ts                 (System/currentTimeMillis)
   :enc-key            (decrypt-enc-key db-user-record password)

   ;; @TODO delete
   :old-enc-key (:old-enc-key db-user-record)
   :pass-info          (let [pass-info (:pass-info db-user-record)]
                         (if (bytes? pass-info)
                           (utils/decode pass-info)
                           pass-info))
   :plan-override-type (:plan-override-type db-user-record)

   :has-mfa            (if (:mfa-key-enc db-user-record) true false)

   ;;wizzard integer and step integer
   :wizz-i             (or (:wiz db-user-record) 0)
   :step-i             (or (:wiz-step db-user-record) 0)

   ;;if user confirmed email or ont
   :confirmed          (if (:confirmed db-user-record) true false)
   })



(def get-user-by-user-name user-registration/get-user-by-user-name')

(def get-user-by-email user-registration/get-user-by-email')