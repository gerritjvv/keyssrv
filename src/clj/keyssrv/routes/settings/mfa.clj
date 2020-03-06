(ns
  ^{:doc "User account reset tokens"}
  keyssrv.routes.settings.mfa
  (:require [ring.util.http-response :as response]
            [one-time.core :as ot]
            [one-time.qrgen :as qrgen]
            [struct.core :as st]
            [keyssrv.users.auth :as auth]
            [keyssrv.layout :as layout]
            [clojure.tools.logging :refer [error]]
            [keyssrv.utils :as utils]
            [keyssrv.routes.users :as users]
            [keyssrv.routes.user-events :as user-events])
  (:import (java.util Date)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; private functions


(defn found [req message errors & {:keys [query-string]}]
  (assoc (response/found (str (:uri req) "?" (if (:query-string req) (str (:query-string req) "&" query-string) query-string)))
    :flash {:errors  errors
            :message message}))

(def mfa-confirm-schema
  [[:mfa-key
    st/required]

   [:code1
    st/required
    st/number-str]

   [:code2
    st/required
    st/number-str]])

(defn check-2fa-code [mfa-key millis code]
  (ot/is-valid-totp-token? code mfa-key {:date (Date. (long millis))}))

(defn codes-match-mfa-key [mfa-key code1 code2]
  (let [seconds30 30000
        now (System/currentTimeMillis)]

    (loop [millis (- now seconds30) cnt 20]
      (when (pos? cnt)
        (if (check-2fa-code mfa-key millis code1)
          (when (check-2fa-code mfa-key (+ millis seconds30) code2)
            true)
          (recur (- millis seconds30) (dec cnt)))))))

(defn validate-codes-match-mfa-key [mfa-key code1 code2]
  (when-not (codes-match-mfa-key mfa-key code1 code2)
    "MFA codes do not match, please try again"))

(defn validate-mfa-confirm [{:keys [params]}]
  (or
    (first (st/validate params mfa-confirm-schema))
    (validate-codes-match-mfa-key (:mfa-key params) (utils/ensure-int (:code1 params)) (utils/ensure-int (:code2 params)))))


(defn gen-qr-base64 [user mfa-key]
  (utils/base64
    (qrgen/totp-stream {:image-type :PNG :image-size 300 :label "pkhub.io" :user (:email user) :secret mfa-key})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; route functions

(defn view [{:keys [flash qr mfa-key] :as request}]
  (let [user (auth/user-logged-in? request)
        db-record (users/get-user-by-id (:id user))]

    (layout/render*
      request
      "dashboard/settings/mfa/main.html"
      (merge
        {:user            user
         :user-name       (:user-name user)
         :security-active "active"
         :mfa-enabled (if (auth/user-mfa-enc db-record) true false)
         :qr  qr
         :mfa-key mfa-key}
        (select-keys flash [:name :message :errors])))))


(defn init-mfa-setup [request]
  (let [user (auth/user-logged-in? request)

        mfa-key (ot/generate-secret-key)
        qr (gen-qr-base64 user mfa-key)]

    (view (assoc request
            :qr qr
            :mfa-key mfa-key))))

(defn confirm-mfa [request]
  (let [user (auth/user-logged-in? request)
        errors (validate-mfa-confirm request)
        message (when (empty? errors) "MFA setup successfully")
        mfa-key (utils/ensure-str (get-in request [:params :mfa-key]))]

    (users/update-mfa-code user mfa-key)


    (view (merge request
                 {:mfa-key mfa-key
                  :qr (gen-qr-base64 user mfa-key)}
                 {:flash {:message message :errors errors}}))))

(defn remove-mfa [request]
  (let [user (auth/user-logged-in? request)]

    (users/remove-mfa user)


    (found
      request
      "Removed MFA"
      nil)))

(defn update-account
  "
  Expects a POST action param for create
  "
  [{:keys [params] :as request}]

  (try
    (let [action (:action params)]
      (cond
        (= action "create") (init-mfa-setup request)
        (= action "confirm") (confirm-mfa request)
        (= action "remove") (remove-mfa request)

        :else
        (do
          (error "Action not supported " action)
          (RuntimeException. (str "Action " action " not implemented")))))
    (catch Exception e
      (error e)
      (found request nil [(str "Internal error while updating plans: " e)]))))


