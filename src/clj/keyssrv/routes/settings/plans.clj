(ns keyssrv.routes.settings.plans
  (:require [keyssrv.users.auth :as auth]
            [keyssrv.layout :as layout]
            [keyssrv.billing.core :as billing]

            [clj-time.format :as t-format]

            [clojure.tools.logging :refer [error]]
            [ring.util.http-response :as response]
            [keyssrv.utils :as utils]
            [keyssrv.routes.users :as users]
            [keyssrv.billing.plans :as billing-plans]
            [keyssrv.routes.user-events :as user-events]))



(defn found [req message errors]
  (assoc (response/found (str (:uri req) "?" (:query-string req)))
    :flash {:errors  errors
            :message message}))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; public route functions

(defonce DATE-FORMAT (t-format/formatter "yyyy/MM/dd"))

(defn make-downgrade-message [[_ next-sub & _]]
  (when next-sub
    (str
      "Plan will be downgraded to " (name (:type next-sub)) " on " (t-format/unparse DATE-FORMAT (:start-date next-sub)) ) ) )

(defn view [{:keys [flash] :as request}]
  (let [user (auth/user-logged-in? request)

        user-active-subs (billing-plans/get-user-subs user)

        record {:user        user
                :user-name   (:user-name user)
                :plan-active "active"
                }
        plan-type (get-in user [:plan :type])

        record' (cond-> record

                        (> (count user-active-subs) 1) (assoc :downgrade-message (make-downgrade-message user-active-subs))

                        (= plan-type :pro) (assoc :is-pro true
                                                  :plan-lbl "Pro")
                        (= plan-type :basic) (assoc :is-basic true
                                                    :plan-lbl "Basic")
                        (= plan-type :free) (assoc :is-free true
                                                   :plan-lbl "Free"))

        record'' (if (> (count user-active-subs) 1)
                   (let [next-sub-type (:type (second user-active-subs))]
                     (cond-> record'
                             (= next-sub-type :free) (assoc :is-downgrade-free true)
                             (= next-sub-type :basic) (assoc :is-downgrade-basic true)))
                   record')]

    (layout/render*
      request
      "settings_plans.html"
      (merge
        record''
        (select-keys flash [:name :message :errors])))))


(defn do-plan-update [request {:keys [plan plan-cycle]}]
  {:pre [(string? plan) (string? plan-cycle)]}

  (let [plan-name (str plan "-" (or plan-cycle "month"))
        user (auth/user-logged-in? request)
        customer-stripe-id (billing/get-db-customer-rel-id user)
        payment-src-id (billing/get-db-payment-src-rel-id user)]

    (cond

      (not (and customer-stripe-id payment-src-id)) (found request nil (str "Please update your billing details"))

      (not plan) (found request nil "Internal error: No plan defined")

      :else (let [_ (auth/user-logged-in? request)
                  _ (billing/ensure-plan user customer-stripe-id plan-name)]

              ;;we refresh the current user in session plan
              (users/refresh-user-current-plan (found request (str "Changed plan") nil)
                                               user)))))

(defn update-account
  "
  Expects a POST action param for create/delete/update
  "
  [{:keys [params] :as request}]
  {:pre [(string? (:plan params)) (string? (:plan-cycle params))]}
  (try
    (let [action (:action params)]
      (cond
        (= action "update") (do-plan-update request {:plan (utils/parse-str-input (:plan params))
                                                     :plan-cycle (utils/parse-str-input (:plan-cycle params))})

        :else
        (do
          (error "Action not supported " action)
          (RuntimeException. (str "Action " action " not implemented")))))
    (catch Exception e
      (error e)
      (found request nil [(str "Internal error while updating plans: " e)]))))