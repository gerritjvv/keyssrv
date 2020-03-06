(ns keyssrv.routes.settings.payment-src
  (:require [keyssrv.users.auth :as auth]
            [keyssrv.layout :as layout]
            [keyssrv.billing.core :as billing]
            [clojure.tools.logging :refer [error]]
            [ring.util.http-response :as response]
            [keyssrv.utils :as utils]
            [keyssrv.routes.user-events :as user-events]))



(defn found [req message errors]
  (assoc (response/found (str (:uri req) "?" (:query-string req)))
    :flash {:errors  errors
            :message message}))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; public route functions

(defn view [{:keys [flash] :as request} & {:keys [show-update-modal]}]
  (let [user (auth/user-logged-in? request)
        payment-src-rel (billing/get-db-payment-src-rel user)]

    (layout/render*
      request
      "dashboard/settings/payment/main.html"
      (merge
        {:user              user
         :user-name         (:user-name user)
         :payment-src       payment-src-rel
         :billing-active    "active"
         :show-update-modal (or show-update-modal false)}
        (select-keys flash [:name :message :errors])))))


(defn do-update-payment-src [request {:keys [stripe-src] :as record}]

  (prn "do-update-payment-src: got record: " record)
  (let [user (auth/user-logged-in? request)
        stripe-card-src-id stripe-src
        _ (billing/ensure-customer stripe-card-src-id user)
        _ (billing/ensure-payment-src stripe-card-src-id (merge user record))]


    (found request "Card updated" nil)))

(defn do-update-account-payment-src
  "Updates the payment and plan src
   require the req params :stripe-src, :card-exp, :card-name, :card-last-4
   "
  [{:keys [params] :as request}]
  (do-update-payment-src request {:stripe-src (utils/parse-str-input (:stripe-src params))
                                  :card-exp (utils/parse-str-input (:card-exp params))
                                  :card-name (utils/parse-str-input (:card-name params))
                                  :card-last-4 (utils/parse-str-input (:card-last-4 params))}))

(defn update-account
  "
  Expects a POST action param for create/delete/update
  "
  [{:keys [params] :as request}]

  (prn "update -account: params " params)

  (try
    (let [action (:action params)]
      (cond
        (= action "update") (do-update-account-payment-src request)


        :else
        (do
          (prn ">>>>>!!!!!!!!!!!!!!!!!!!!! action => " action)
          (error "Action not supported " action)
          (RuntimeException. (str "Action " action " not implemented")))))
    (catch Exception e
      (error e)
      (found request nil [(str "Internal error while updating groups: " e)]))))