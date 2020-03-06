(ns
  ^{:doc
    "Everything to do with authentication"}
  keyssrv.users.auth
  (:require [keyssrv.routes.user-utils :as user-utils]
            [keyssrv.users.registration :as registration]
            [keyssrv.users.login :as user-login]
            [ring.util.http-response :as response]
            [keyssrv.schemas.core :as schemas]
            [keyssrv.secret.keys :as keys]
            [keyssrv.db.core :as db]
            [one-time.core :as ot]
            [clj-time.core :as t]
            [keyssrv.secret.appkeys :as akeys]
            [clojure.tools.logging :as log])
  (:import (org.apache.commons.lang3 StringUtils)
           (java.util Base64)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;; user helper functions
;;see keyssrv.routes.users/user-record
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn session-id
  "Get the request session id"
  [request]
  (:session/key request))

(defn update-user-wizz-i [user wiz-i step-i]
  (assoc user
    :wizz-i wiz-i
    :step-i step-i))

(defn user-wizz-i [user]
  (:wizz-i user))

(defn user-step-i [user]
  (:step-i user))

(defn user-plan [user]
  (:plan user))

(defn user-enc-key [user]
  (:enc-key user))

(defn user-name [user]
  (:user-name user))

(defn user-pass-hash [user]
  (:pass-hash user))

(defn user-mfa-enc [user]
  (:mfa-key-enc user))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn as-long [v]
  (when v
    (if (string? v)
      (Long/valueOf (str v))
      (long v))))

(def middleware-user-logged-in? user-login/middleware-user-logged-in?)
(def user-logged-in? user-login/user-logged-in?)

(defn user-has-mfa?
  "User record"
  [user]
  (:has-mfa user))

(defn user-confirmed-mfa? [{:keys [session]}]
  (:confirmed-mfa session))

(defn do-session-confirm-mfa
  "Update the response session"
  [user resp]
  (update resp :session #(assoc % :confirmed-mfa true
                                  :identity user)))

(defn login-user-session
  "Return a request with the user-record added as an identity to the request session"
  [request user-record]
  (schemas/must-be-valid! schemas/USER-SESSION-IDENTITY user-record)
  (update request :session #(assoc % :identity user-record)))


(defn mfa-key-match?
  "Decrypts the mfa-key-enc and checks the onetime mfa code against the mfa-key
   return true if the code is valid"
  [user user-mfa-enc code]
  (when user-mfa-enc
    (let [mfa-key (keys/decrypt (user-enc-key user) user-mfa-enc)]
      (ot/is-valid-totp-token? code mfa-key))))

(defn get-app-key-record
  "Returns nil or a record with the user information and app-key entry combined
    {:user-id :key-id :key-secret-hash :date-created :date-expire
     :user-name :email :pass-hash :enc-key :mfa-key-enc :confirmed :org}
     The :id value is that of the user record's id
  "
  [k]
  {:pre [(string? k)]}
  (let [record (first
                 (db/get-user-app-key-by-id {:key-id k}))]

    (when record
      (assoc record
        :id (:user-id record)))))

(defn valid-app-key?
  "If the app-key-record hash and secret matches the app-key-record is returned"
  [app-key-record secret]

  ;;@TODO check keys for bcrypt and use argon2
  ;; for keys for now we'll use bcrypt

  (when (and
          ;;check we have a valid user and api key
          (:user-name app-key-record)
          (t/before? (t/now) (:date-expire app-key-record))

          ;;check hash
          (keys/check-api-key-against-hash nil secret (:key-secret-hash app-key-record)))

    app-key-record))

(defn api-user-basic-login [user-name pass]
  (let [{:keys [errors db-user-record]} (registration/get-user-record-and-login {:user-name user-name :password pass})]
    (when-not errors
      db-user-record)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; auth middleware

(defn wrap-key-authentication
  "authorization-header => string of key:secret
   api-user-handler => (fn [user])

   if the user is not authenticated then "
  [authorization-header api-user-handler]
  (let [[app-key secret] (StringUtils/split (str authorization-header) \:)
        user (when (and app-key secret) (valid-app-key? (get-app-key-record app-key) secret))

        secret-bts (try
                     (akeys/key-secret-as-bytes secret)
                     (catch Exception e
                       (log/error e)
                       nil))
        ;; see users/user-record, this is used by the UI to decrypt the enc-key
        ;; but to do this we need to swap in the app-key's encrypted enc_key instead of the user's enc_key
        ;; note this is done automatically, because our sql query has app_keys.enc_key as enc-key and the user.enc_key as enc-key-2

        user' (when (and user secret-bts)
                (user-utils/user-record (assoc
                                          (user-utils/add-current-plan user)
                                          :pass-hash secret-bts)
                                        {:user-name (:user-name user)
                                         :password  secret}))]

    (if-not user'
      (response/unauthorized)
      (api-user-handler user'))))

(defn parse-basic-auth [header]
  (let [[_ userpass-b64] (StringUtils/split (str header) \:)
        userpass-b64' (StringUtils/trimToNull (str userpass-b64))

        decoded (when userpass-b64' (String. (.decode (Base64/getDecoder) userpass-b64') "UTF-8"))

        [user pass] (when decoded (StringUtils/split (str decoded) \:))]

    (when (and user pass)
      [user pass])))

(defn wrap-basic-authentication [authorization-header api-user-handler]

  (let [[user-name password] (parse-basic-auth authorization-header)
        user (api-user-basic-login user-name password)

        user' (when (not-empty user)
                (user-utils/user-record (user-utils/add-current-plan user)
                                        {:user-name (:user-name user)
                                         :password  password}))]


    ;
    (if-not user'
      (response/unauthorized)
      (api-user-handler user'))))

(defn isBasicAuth
  "True is Basic: is in the prefix of the authorization-header (c
  ase insensitive)"
  [authorization-header]
  (StringUtils/startsWith (StringUtils/lowerCase (StringUtils/trim (str authorization-header))) "basic"))


(defn wrap-api-authentication
  "authorization-header => string of key:secret
   api-user-handler => (fn [user])

   if the user is not authenticated then "
  [authorization-header api-user-handler]
  (if (isBasicAuth authorization-header)
    (wrap-basic-authentication authorization-header api-user-handler)
    (wrap-key-authentication authorization-header api-user-handler)))

(defn wrap-authentication
  "If a user is not logged in, we redirect back to login, otherwise continue
    see keyssrv.handler where routes are wrapped"
  [handler]
  (fn [request]
    (let [{:keys [user errors]} (middleware-user-logged-in? request)]
      (if user
        (if (and (user-has-mfa? user) (not (user-confirmed-mfa? request)))
          (response/found "/mfa")
          (handler request))
        ;(schemas/valid? schemas/USER-SESSION-IDENTITY user)
        (assoc
          (response/found "/login")
          :flash {:errors errors})))))

(defn delete-account!
  "Delete the user and everyting user related"
  [user]
  (db/delete-user! {:id (:id user)}))

