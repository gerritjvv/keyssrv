(ns keyssrv.routes.settings.account
  (:require [keyssrv.users.auth :as auth]
            [keyssrv.layout :as layout]
            [keyssrv.secret.keys :as keys]
            [keyssrv.routes.users :as users]
            [keyssrv.notification.notify :as notify]
            [keyssrv.db.core :as db]
            [clojure.tools.logging :refer [error]]
            [ring.util.http-response :as response]
            [keyssrv.utils :as utils]
            [keyssrv.routes.user-events :as user-events]))



(defn found [req message errors]
  (assoc (response/found (str (:uri req) "?" (:query-string req)))
    :flash {:errors  errors
            :message message}))

(defn send-verification-code [user]
  (let [code (keys/gen-verification-key)]
    (notify/send-verification-code-email (:email user) code)
    ;;set code as token and expire in 10 minutes
    ;;to verify the token only needs to exist
    (keyssrv.tokens.core/set-token (str (:id user)) (str (:id user) "_" code) true (* 60 10))


    code))


(defn valid-verification-code? [user code]
  (if (keyssrv.tokens.core/get-token (str (:id user)) (str (:id user) "_" code))
    (do
      (keyssrv.tokens.core/del-token (str (:id user) "_" code))
      true)
    false))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; public route functions

(defn view [{:keys [flash] :as request} & {:keys [show-update-modal]}]
  (let [user (auth/user-logged-in? request)]

    (layout/render*
      request
      "dashboard/settings/account/main.html"
      (merge
        {:user              user
         :user-name         (:user-name user)
         :account-active    "active"
         :show-update-modal (or show-update-modal false)}
        (select-keys flash [:name :message :errors])))))


(defn do-show-account [request]
  (let [user (auth/user-logged-in? request)
        _ (send-verification-code user)]

    (view (assoc request
            :flash {:message "Verification code emailed"})

          :show-update-modal true)))

(defn validate-update [record]
  (users/validate-user-update (utils/remove-nil-vals record)))

(defn check-password-hash [user current-password]
  (users/validate-password-hash user current-password))

(defn keep-only-update-values
  "Values that match and have not changed are removed"
  [{:keys [email user-name]} {:keys [new-email new-user-name] :as input-record}]
  {:pre [email user-name]}

  (cond-> input-record

          (and new-email
               (= email new-email)) (dissoc :new-email)

          (and new-user-name
               (= new-user-name user-name)) (dissoc :new-user-name)))

(defn do-update-account [request
                         input-record]
  (let [user (auth/user-logged-in? request)
        {:keys [code
                current-password
                new-email
                new-user-name
                new-password] :as record} (keep-only-update-values user input-record)
        errors (validate-update record)]

    (cond
      (not code) (found request nil "No verification code provided, please check your email")
      (not (valid-verification-code? user code)) (found request nil "The verification code is not valid, please try again")

      ;; any validation errors, email password retype
      (not-empty errors) (found request nil (vals errors))

      ;; check password hash
      (and new-password
           (check-password-hash user current-password)) (found request nil "The current password is incorrect")

      ;; nothing to update
      (not (or
             new-password
             new-email
             new-user-name)) (found request nil "Nothing to update")

      ;;try update
      :else (let [_ (users/update-new-password user (:pass-info user) new-password (:enc-key user))
                  update-m (cond-> {}
                                   (and
                                     new-email
                                     (not= new-email (:email user))) (assoc :email new-email)

                                   (and new-user-name
                                        (not= new-user-name (:user-name user))) (assoc :user_name new-user-name))]


              (if (not-empty update-m)
                (do
                  (db/update-account-info! {:updates update-m
                                            :id      (:id user)})

                  ;;we need to logout and log in back again to ensure the session is correct
                  (users/logout request))

                (found request nil nil))))))

(defn do-delete-account [request]

  (let [user (auth/user-logged-in? request)
        delete-me-check (utils/ensure-str (:delete-me-check (:params request)))]

    (if (= (str delete-me-check) "delete me")
      (do
        (auth/delete-account! user)
        (dissoc
          (response/found "login")
          :session))
      (found request nil "To delete the account type in the text 'delete me'"))))

(defn update-account
  "
  Expects a POST action param for create/delete/update
  "
  [{:keys [params] :as request}]

  (try
    (let [action (:action params)]
      (cond
        (= action "show") (do-show-account request)
        (= action "delete") (do-delete-account request)

        (= action "update") (do-update-account request
                                               {:new-user-name       (utils/parse-str-input (:new-user-name params))
                                                :new-email           (utils/parse-str-input (:new-email params))
                                                :current-password    (utils/parse-str-input (:current-password params))
                                                :new-password        (utils/parse-str-input (:new-password params))
                                                :new-password-retype (utils/parse-str-input (:new-password-retype params))
                                                :code                (utils/parse-str-input (:code params))})


        :else
        (RuntimeException. (str "Action " action " not implemented"))))
    (catch Exception e
      (error e)
      (found request nil [(str "Internal error while updating groups: " e)]))))