(ns
  ^{:doc "Rest routes that have to do with user crud"}
  keyssrv.routes.users
  (:require
    [keyssrv.routes.user-utils :as user-utils]
    [keyssrv.db.core :as db]
    [keyssrv.pwd.checks :as checks]
    [keyssrv.config :as config]
    [keyssrv.users.registration :as user-registration]
    [keyssrv.tokens.core :as tokens-core]
    [keyssrv.routes.user-events :as user-events]
    [keyssrv.routes.index.wizzards :as wizzards]
    [keyssrv.routes.index.wizzard-data :as wizzard-data]

    [keyssrv.routes.route-utils :as route-utils]
    [keyssrv.routes.settings.reset-tokens :as reset-tokens]
    [keyssrv.users.auth :as auth]
    [keyssrv.sessions :as sessions]
    [struct.core :as st]
    [ring.util.response :refer [redirect]]
    [ring.util.http-response :as response]
    [clojure.tools.logging :as log]
    [clj-http.client :as clj-http]
    [keyssrv.layout :as layout]
    [keyssrv.secret.keys :as keys]
    [keyssrv.notification.notify :as notify]
    [keyssrv.utils :as utils])
  (:import (org.apache.commons.lang3 StringUtils)))

(defn check-pwd-minimum-length [pwd]
  (>= (count pwd) 6))

(defn check-common-pwds [pwd]
  (not (checks/is-common-pwd? pwd)))

(defn get-user-by-id [user-id]
  (db/get-user-by-id {:id user-id}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;; schemas

(defn validate-username-unique [user-name]
  (not
    (let [user (db/get-user-by-user-name {:user-name user-name})]
      (when (:email user)
        user))))

(defn validate-email-unique [email]
  (not
    (let [user (db/get-user-by-email {:email email})]
      (when (:email user)
        user))))

(def user-update-schema
  [[:new-email
    st/email

    {:message  "Another user with this email has already been registered"
     :validate validate-email-unique}]

   [:new-user-name
    st/string
    {:message  "Username is already in use"
     :validate #(or (not %)
                    validate-username-unique)}]

   [:new-password
    {:message  "Password must be minimum of 6 characters and not one of the 1000 most common passwords"
     :validate #(or (not %)
                    (and
                      (check-pwd-minimum-length %)
                      (check-common-pwds %)))}]])

(def user-mfa-login-confirm-schema
  [[:mfa-code
    st/required
    st/integer-str]])

(def user-reset-request-schema
  [[:email
    st/required
    st/email]

   [:reset-token
    st/required
    st/string]])

(def user-register-schema
  [[:email
    st/required
    st/email

    {:message  "Another user with this email has already been registered"
     :validate validate-email-unique}]

   [:user-name
    st/required
    st/string
    {:message  "Username is already in use"
     :validate validate-username-unique}]
   [:password
    st/required
    {:message  "Password must be minimum of 6 characters and not one of the 1000 most common passwords"
     :validate #(and
                  (check-pwd-minimum-length %)
                  (check-common-pwds %))}]])

(def user-create-new-pass-schema
  [[:password
    st/required
    {:message  "Password must be minimum of 6 characters and not one of the 1000 most common passwords"
     :validate #(and
                  (check-pwd-minimum-length %)
                  (check-common-pwds %))}]])

(def user-login-schema
  [[:user-name
    st/required
    st/string]

   [:password
    st/required]])

(defn passwords-update-match [params]
  (when-not (=
              (:new-password params)
              (:new-password-retype params))
    [{:password "Passwords do not match"} nil]))

(defn passwords-match [params]
  (when-not (=
              (:password params)
              (:password-retype params))
    [{:password "Passwords do not match"} nil]))


(defn validate-known-bad-emails [{:keys [email]}]
  (let [^String email' (StringUtils/lowerCase (str email))]
    (when
      (or
        (StringUtils/contains email' "@example")
        (StringUtils/endsWith email' "@gmail.co")
        (StringUtils/contains email' "@gmial")
        (StringUtils/contains email' "@hotmial")
        (StringUtils/contains email' "@gamil")
        (StringUtils/contains email' "@com.tr")
        (StringUtils/contains email' "you@")
        (StringUtils/contains email' " @dkfrmf.com")
        (StringUtils/contains email' "@epirussa.gr")
        (StringUtils/contains email' " @qwert")
        (StringUtils/contains email' " @123")
        (StringUtils/contains email' "dkfrmf.com")
        (StringUtils/contains email' "gemail.com")
        (StringUtils/contains email' "perezimail.com")
        (StringUtils/contains email' "ejemplo.com")
        (StringUtils/contains email' "@l...flc")
        (StringUtils/contains email' "@v1.com")
        (StringUtils/contains email' "@longhorn.con")
        (StringUtils/contains email' "@outlook.com")

        )

      [{:email "Bad email, please provide a correct email"} nil])))


(defn validate-user-update [params]
  (or
    (first (st/validate params user-update-schema))
    (first (passwords-update-match params))))

(defn validate-user-registration [params]
  (or
    (first (st/validate params user-register-schema))
    (first (validate-known-bad-emails params))
    (first (passwords-match params))))

(defn validate-user-pass-recreate [params]
  (or
    (first (st/validate params user-create-new-pass-schema))
    (first (passwords-match params))))

(defn validate-user-reset-request [params]
  (first
    (st/validate params user-reset-request-schema)))

(def validate-password-hash user-registration/validate-password-hash)

(defn validate-user-login
  "Return any true value is an error, normally the validation error "
  [db-user-record params]
  (or
    (first (st/validate params user-login-schema))
    (user-registration/validate-user-login db-user-record params)))


;;;;; login logic
(def user-record user-utils/user-record)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;; routes



;;;;; login

(defn show-login [{:keys [flash] :as request}]
  (layout/render*
    request
    "login.html"
    (select-keys flash [:name :message :errors])))


(def get-user-by-user-name user-utils/get-user-by-user-name)
(def get-user-by-email user-utils/get-user-by-email)

(defn update-user-session
  "Adds the user to the session"
  [resp user]
  (auth/login-user-session
    resp
    user))

(def add-current-plan user-utils/add-current-plan)


(defn refresh-user-current-plan
  "Take a response and user and refresh the user's plan from the active subscriptions"
  [resp user]
  (update-user-session resp (add-current-plan user)))


(defn do-login-user [db-user-record {:keys [params]} & {:keys [after-register]}]
  (let [resp (auth/login-user-session
               (response/found (if after-register
                                 "/homeregister"
                                 "/home"))
               (user-record db-user-record params))
        ]

    resp))


(defn ensure-user-name-from-paras
  "Return the request with the params user-name updated to that of the db-user-record"
  [db-user-record request]
  (assoc-in request [:params :user-name] (:user-name db-user-record)))

(defn login-user
  "
   To see the function that checks if a user exists and validates the user see: get-user-record-and-login
  "
  [{:keys [params] :as request} & {:keys [after-register]}]
  ;;we support login by email or user-name the user-name might be the email
  ;;the registration system checks that all emails and all user names are unique

  (let [{:keys [errors db-user-record]} (user-registration/get-user-record-and-login params)]
    (if errors
      (route-utils/found "login" nil errors)
      (let [user (add-current-plan db-user-record)]


        (do-login-user user (ensure-user-name-from-paras user
                                                         request)
                       :errors (:errors user)
                       :after-register after-register)))))

(defn logout [req]
  ;;do not just remove the session
  ;; we need to actually dissoc the identity and remove the session from the store

  (sessions/remove-session req)
  (update-in
    (response/found "login")
    [:session :identity]
    {}))

;;;;; register


;;@TODO deny access if not valided lets see the output first
(defn validate-recapcha [req user captcha]
  (if (:captcha-secret config/env)
    (try
      (let [{:keys [body]} (clj-http/post
                             "https://www.google.com/recaptcha/api/siteverify"
                             {:socket-timeout 1000
                              :conn-timeout   1000
                              :form-params
                                              {:secret   (:captcha-secret config/env)
                                               :response captcha
                                               :remoteip (:ip req)}})
            {:strs [success]} (cheshire.core/parse-string body)]

        (when-not success
          ;;Capture the failed event
          "Recapcha failed"))
      (catch Exception e
        (log/error e)
        nil))
    (do
      (log/error
        "No captcha secret provided, skipping validation")
      nil)))

(defn translate-to-plan
  "The values used in the ui should be translated to meaning full keywords"
  [plan]
  (case plan
    "0" :free
    "1" :basic
    "2" :pro
    "10" :start
    :free))

(defn translate-plan-period [plan-period]
  (case plan-period
    "year" :year
    "month" :month
    :year))

(defn register-user
  "Add a user to the db, login and redirect to the home page"
  [{:keys [params] :as req}]
  (let [user-name (utils/ensure-str (:user-name params))
        email (utils/ensure-user-email (:email params))

        plan (translate-to-plan (utils/ensure-str (:plan params)))

        plan-period (translate-plan-period (utils/ensure-str (:p params)))

        user-name' (utils/ensure-user-name (if (empty? user-name) email user-name))

        password (utils/ensure-str (:password params))

        [wizz-i step-i] (wizzards/get-wizzard-ints ::wizzards/setup ::wizzards/setup-plan)

        record {:email           email
                :password        password
                :password-retype password                   ;;removing re-type and replacing with show/hide
                :user-name       user-name'
                :wizz-i          wizz-i
                :step-i          step-i}
        ]

    (if-let [errors (validate-user-registration record)]
      (route-utils/found "register" nil (first (vals errors)))
      (let [user (user-registration/create-registered-user record)
            ;;add the data for plan and period that the user selected or may have not selected
            ;;default is free, which means we'll show the user the pricing again during the setup wizzard
            _ (wizzard-data/add-wizzard-plan-data user plan plan-period)]


        ;;after register login
        (when (validate-recapcha req user (:g-recaptcha-response params))
          ;;means failed
          (user-events/user-visit! user "recapcha-fail" "/register"))

        ;;send verification email, only when a free account
        (when (= :free plan)
          (notify/send-register-confirm-email (:email user) (:confirm-code
                                                              (reset-tokens/gen-user-email-confirm-code user))))

        (notify/send-welcome-email (:email user) user-name')

        (login-user (assoc-in req [:params :user-name] user-name'))))))

(defn show-registration [{:keys [flash params] :as request}]
  (layout/render*
    request
    "register.html"
    (assoc
      (select-keys flash [:name :message :errors])
      :plan (:plan params)                                  ;;plan id [0 free 1 basic 2 pro]
      :p (:p params)                                        ;;year/month
      )))

(defn do-send-reset [user-email token-id]
  (notify/send-reset-email user-email (str (:public-address config/env) "/reset/email?token=" token-id)))

(defn do-reset-login' [{:keys [params] :as request}]
  (let [errors (validate-user-reset-request params)

        user (when (empty? errors) (db/get-user-by-email {:email (utils/ensure-user-email (:email params))}))
        reset-token (utils/ensure-str (str (:reset-token params)))

        reset-token-record (when (not-empty user) (reset-tokens/get-reset-token-and-delete user reset-token))


        token-id (when (not-empty reset-token-record)
                   ;;reset-token-record is encrypted with the system key
                   (let [id (tokens-core/unique-id)]
                     (tokens-core/set-token (auth/session-id request)
                                            id
                                            {:user-id (:id user)
                                             :token   reset-token-record} (* 15 60))))]

    (cond
      (not-empty errors) (route-utils/found "reset" nil (vals errors))
      (empty? user) (route-utils/found "reset" nil ["Email not registered"])
      (empty? reset-token-record) (route-utils/found "login" nil ["Reset token is not valid"])

      :else
      (do
        (do-send-reset (:email params) token-id)
        (route-utils/found "login" "A reset link has been sent to your email" nil)))))


(defn update-mfa-code [user mfa-key]
  (let [enc-val (keys/encrypt (auth/user-enc-key user) mfa-key)]

    (user-registration/update-mfa-code user
                                       enc-val)

    (let [record (get-user-by-id (:id user))]
      (keys/decrypt (auth/user-enc-key user) (auth/user-mfa-enc record)))))

(defn update-new-password
  "Update the user password after a password reset
   saving the pass hash and encrypting the enc-key with the user's password"
  [user pass-info' pass enc-key]
  (let [{:keys [pass-info pass-hash]} (keys/password-hash pass-info' pass)]

    (user-registration/update-user-pass-and-enc user
                                                pass-info
                                                pass-hash
                                                enc-key)))

(defn remove-mfa
  "On password reset, we should remove mfa also"
  [user]
  (db/delete-user-mfa! {:id (:id user)}))

(defn do-create-new-pass
  "Read the token from do-reset-login', validate and
   update the new user password
    see login-reset-createpass.html"
  [{:keys [params]}]

  (let [errors (validate-user-pass-recreate params)

        token-id (:token params)

        {:keys [user-id token]} (when token-id (tokens-core/get-token token-id token-id))

        user (when user-id (get-user-by-id user-id))]


    (cond
      (not-empty errors) (route-utils/found (str "reset?token=" token-id) nil (first (vals errors)))
      (not token-id) (route-utils/found "reset" nil "Internal Error: Token id is not defined")
      (empty? token) (route-utils/found "reset" nil "Invalid token or token has expired")
      (empty? user) (route-utils/found "reset" nil "The user does not exist anymore")
      :else (let [pass (:password params)]

              (when (or
                      (empty? pass)
                      (empty? (:enc-key token)))
                (throw (RuntimeException. (str "Internal Error: The password can never be null here"))))


              (update-new-password user (:pass-info user) pass (:enc-key token))
              (remove-mfa user)

              (route-utils/found "login" "Password was reset, please login with the new password" nil)))))

(defn do-reset-login [{:keys [params] :as request}]
  (let [action (:action params)]
    (cond
      (= action "createpass") (do-create-new-pass request)
      (= action "reset") (do-reset-login' request)
      :else
      (throw (RuntimeException. (str "Action " action " not supported"))))))


(defn view-reset-login-email
  "The email link sent redirects to this function which
   uses the redirectlogin_createpass to redirect into the proper reset
   screen to avoid csrf issues"
  [{:keys [params] :as request}]
  (layout/render*
    request
    "redirectlogin_createpass.html"
    {:token (:token params)}))

(defn view-reset-login [{:keys [flash params] :as request}]
  (if-let [token-id (:token params)]
    ;; @TODO we need the session id here
    (let [token (tokens-core/get-token token-id token-id)]

      (if (nil? token)
        (layout/render*
          request
          "login_reset.html"
          {:errors ["Invalid token or token has expired"]})
        (layout/render*
          request
          "login_reset_createpass.html"
          (merge (select-keys flash [:name :message :errors])
                 {:token-id token-id}))))
    (layout/render*
      request
      "login_reset.html"
      (select-keys flash [:name :message :errors]))))


(defn do-confirm-mfa-login [request]
  (let [user (auth/user-logged-in? request)]
    (auth/do-session-confirm-mfa
      user
      (route-utils/found "/home" nil nil))))

(defn update-mfa-login
  "Called by the login_mfa.html, check the mfa code provided and mark the session
   as mfa confirmed, continue to home"
  [{:keys [params] :as request}]
  (let [user (auth/user-logged-in? request)
        errors (first (st/validate params user-mfa-login-confirm-schema))

        user-db-record (when (empty? errors) (get-user-by-id (:id user)))]

    (cond
      errors (route-utils/found "mfa" nil errors)

      (auth/mfa-key-match? user (auth/user-mfa-enc user-db-record) (utils/ensure-int (:mfa-code params))) (do-confirm-mfa-login request)

      :else
      (route-utils/found "mfa" nil "Invalid mfa code"))))


(defn view-mfa
  "Show the mfa screen.
   MFA logins are controlled from the login middleware
  "
  [{:keys [flash] :as request}]
  (layout/render*
    request
    "login_mfa.html"
    (select-keys flash [:name :message :errors])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; check if a user name exist or not
(defn check-user-name-exist [{:keys [params]}]
  (let [user (get-user-by-user-name (assoc params
                                      :user-name
                                      (:check-value params)))]

    (layout/ajax-response
      {:resp (if (empty? user) "0" "1")})))

(defn check-email-exist [{:keys [params]}]
  (let [user (get-user-by-email (assoc params
                                  :email
                                  (:check-value params)))]
    (layout/ajax-response
      {:resp (if (empty? user) "0" "1")})))

(defn check-exist [{:keys [params] :as request}]
  (let [check-type (:check-type params)]

    (case check-type
      "user-name" (check-user-name-exist request)
      "email" (check-email-exist request)
      (layout/ajax-response {:resp "-1"}))))



