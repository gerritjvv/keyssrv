(ns
  ^{:doc "Where a user starts after logging register or login"}
  keyssrv.routes.index.home
  (:require [keyssrv.routes.index.wizzards :as wizz]
            [keyssrv.routes.settings.payment-src :as payment-src]
            [keyssrv.users.registration :as auth-reg]
            [keyssrv.routes.settings.plans :as plans]
            [keyssrv.routes.route-utils :as route-utils]
            [keyssrv.users.auth :as auth]
            [keyssrv.routes.users :as users]
            [keyssrv.utils :as utils]
            [keyssrv.routes.settings.reset-tokens :as reset-tokens]
            [keyssrv.routes.user-events :as user-events]
            [keyssrv.routes.index.wizzard-data :as wizzard-data]
            [keyssrv.notification.notify :as notify]
            [keyssrv.layout :as layout]))


(def TEMPLATE "front/setup_wizzard.html")


(defn view-setup-plan
  "Wizz-data is from the wizzard_data table created on registration, contains the plan {:free :basic :pro}
  and plan-period {:year :month}.
  when plan is not :free, we skip the plan select part in the wizzard and ask to user for his/her credit card to continue."
  [user req wizz-data]
  (route-utils/render
    TEMPLATE
    req
    {:pixel-event              "wizz-plan"


     :pre-selected-plan        (or (:plan wizz-data) :free)
     :pre-selected-plan-period (or (:plan-period wizz-data) :year)

     :WIZZ_PLAN                true
     :step-i                   (auth/user-step-i user)}))

(defn view-setup-confirm [user req]
  (route-utils/render
    TEMPLATE
    req
    {:pixel-event  "wizz-confirm"
     :WIZZ_CONFIRM true
     :step-i       (auth/user-step-i user)}))

(defn view-setup-getting-started [user req]
  (route-utils/render
    TEMPLATE
    req
    {:pixel-event          "wizz-started"
     :WIZZ_GETTING_STARTED true
     :step-i               (auth/user-step-i user)}))

(defn view-setup-wizzard-step [user req step-k]
  (let [wizz-data (wizzard-data/get-wizzard-plan-data user)]
    (condp = step-k
      ::wizz/setup-plan (view-setup-plan user req wizz-data)
      ;::wizz/setup-confirm (view-setup-confirm user req)
      ::wizz/setup-getting-started (view-setup-getting-started user req)
      (route-utils/found "/pass/groups"))))

(defn update-session-step-i [step-i req]
  (update-in req [:session :identity :step-i] (fn [_] step-i)))

(defn update-wizzard-next-step [req]
  ;;the params step-i ensures we only move to the next step if the user is viewing the correct current step

  ;(prn "update-wizzard-next-step pre " {:params-step-i-str (get-in req [:params :step-i])
  ;                                      :params-step-i     (utils/ensure-int (get-in req [:params :step-i]))
  ;                                      :user-step-i       (auth/user-step-i (auth/user-logged-in? req))})
  (let [user (auth/user-logged-in? req)
        curr-step (auth/user-step-i user)
        param-step-i (utils/ensure-int (get-in req [:params :step-i]))]
    (if (<= curr-step param-step-i)
      (let [
            [curr-wiz-k curr-step-k curr-wiz-def] (wizz/get-wizzard (auth/user-wizz-i user) (auth/user-step-i user))
            next-step-k (wizz/step-forward curr-wiz-def curr-step-k)

            [wizz-i step-i] (wizz/get-wizzard-ints curr-wiz-k next-step-k)]

        (auth-reg/update-wizzard-db-data user wizz-i step-i)
        step-i)
      curr-step)))


(defn update-plan-and-payment-info [req]
  {:pre [(string? (get-in req [:params :plan]))
         (string? (get-in req [:params :plan-cycle]))]}
  (let [user (auth/user-logged-in? req)
        req' (assoc-in req [:params :action] "update")

        ;;require (:stripe-src params)

        flash-acc (:flash (payment-src/update-account req'))
        flash-plan (when (empty? (:errors flash-acc)) (:flash (plans/update-account req')))

        step-i (if-not (or (:errors flash-acc)
                           (:errors flash-plan))
                 (update-wizzard-next-step req)
                 (auth/user-step-i user))


        ;; @TODO update plan wizz details to the selected plan, maybe do one plan higher

        errors (or (:errors flash-acc)
                   (:errors flash-plan))
        message (when-not errors
                  (or (:message flash-acc)
                      (:message flash-plan)))

        resp-home (route-utils/found "/home"
                                     message
                                     errors)

        ]


    (update-session-step-i step-i
                           (users/refresh-user-current-plan resp-home user))))

(defn update-user-confirmed [user]
  (auth-reg/set-user-email-confirmed user))

(defn rate-limit-resend
  "return true if the ts val in the confirm token is more than time-elapsed-sec"
  [user time-elapsed-sec]
  (let [{:keys [ts] :or {ts 0}} (reset-tokens/get-email-confirm-tokens-by-id user)
        ts-now (System/currentTimeMillis)]

    (> (- ts-now (long ts))
       (* 1000 (long time-elapsed-sec)))))

(defn resend-email-ajax! [req]
  (let [user (auth/user-logged-in? req)]
    (when user

      (when (rate-limit-resend user 10)
        (notify/send-register-confirm-email (:email user) (:confirm-code (reset-tokens/gen-user-email-confirm-code user))))

      (layout/ajax-response {:resp "ok"}))))

(defn confirm-user-email [req]
  (let [params (:params req)
        user (auth/user-logged-in? req)
        confirm-code (utils/ensure-str (:confirm-code params))
        next-step-i (update-wizzard-next-step req)

        valid-confirm-code (when confirm-code
                             (reset-tokens/get-email-confirm-tokens-by-id user))
        ]

    (cond
      (or
        (not confirm-code)
        (not valid-confirm-code)
        (not= confirm-code
              (:confirm-code valid-confirm-code))) (route-utils/found-same-page req nil "Please enter a valid Email Confirm Key")

      valid-confirm-code (do
                           (update-user-confirmed user)
                           (update-session-step-i next-step-i
                                                  (users/refresh-user-current-plan (route-utils/found-same-page req "Confirmed Email" nil) user)))

      :else
      (update-session-step-i next-step-i
                             (users/refresh-user-current-plan (route-utils/found-same-page req "Invalid Email Confirm Key" nil) user)))))

(defn skip-step [req]
  (let [user (auth/user-logged-in? req)
        next-step-i (update-wizzard-next-step req)]

    (user-events/user-visit! user "wizz-skip" "/home")

    (update-session-step-i next-step-i
                           (users/refresh-user-current-plan (route-utils/found-same-page req nil nil) user))))

(defn get-plan-and-cycle-from-form [params]
  (prn "params: " params)
  (cond
    (= (:start-year params) "on") ["start" "year"]
    (= (:start-month params) "on") ["start" "month"]

    (= (:basic-year params) "on") ["basic" "year"]
    (= (:basic-month params) "on") ["basic" "month"]

    (= (:pro-year params) "on") ["pro" "year"]
    (= (:pro-month params) "on") ["pro" "month"]

    :else ["free" "month"]))


(defn wizz-update
  "Called by the browser POST to validate the wizzard data"
  [{:keys [params] :as req}]

  (let [
        [plan plan-cycle] (get-plan-and-cycle-from-form params)
        action (:action params)
        ;; plan and plan-cycle corresponds to a plan name in the database
        ;; Dev/Prod%[plan]-[cycle] e.g for env=Prod plan=basic cycle=year
        ;; the product_plans table must have a product with Prod-plan-year
        ]


    (prn "home/wizz-update: " {:plan plan :action action})

    (cond

      ;; resend email button to get a new email confirm code, this is an ajax send
      ;; TODO to re-allow this feature we need to have a reate limiter, this is dangerous activate without one.
      (= action "resend-confirm")  (confirm-user-email req);(resend-email-ajax! req)



      (= plan "free") (confirm-user-email req)
      (not (utils/parse-str-input (:stripe-src params))) (route-utils/found "/home"
                                                                             ""
                                                                             "Cannot process your card details, please check you have filled out the form correctly.")

      :else (update-plan-and-payment-info (update-in req
                                                     [:params]
                                                     assoc
                                                     :plan plan
                                                     :plan-cycle plan-cycle)))
    ))

(defn view [req]
  (let [user (auth/user-logged-in? req)
        wizz-i (auth/user-wizz-i user)
        step-i (auth/user-step-i user)
        [wizz-k step-k _] (when step-i (wizz/get-wizzard wizz-i step-i))]


    (cond
      (and
        (= ::wizz/setup wizz-k)
        (not= ::wizz/setup-end step-k)) (view-setup-wizzard-step user req step-k)

      (= ::wizz/setup-end step-k) (let [[next-wiz-i next-step-i] (wizz/get-wizzard-ints ::wizz/init-hints ::wizz/init-hints-show-group)]

                                    (auth-reg/update-wizzard-db-data user next-wiz-i next-step-i)
                                    (users/update-user-session (route-utils/found-same-page req nil nil)
                                                               (auth/update-user-wizz-i
                                                                 user
                                                                 next-wiz-i
                                                                 next-step-i)))
      :else (route-utils/found "/pass/groups"))))






