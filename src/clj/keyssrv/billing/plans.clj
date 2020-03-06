(ns keyssrv.billing.plans
  (:require [keyssrv.db.core :as db]
            [keyssrv.config :as config]
            [keyssrv.schemas.core :as schemas]
            [keyssrv.billing.api :as billing-api]
            [clj-time.core :as time]
            [clj-time.coerce :as t-coerce]
            [mount.core :as mount])
  (:import (org.apache.commons.lang3 StringUtils)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; private functions

(defn make-type-keyword [plan]
  (update plan :type keyword))

(defn plan-of-type? [plan plan-type]
  (= (:type plan) plan-type))

(defn plan-by-name [plan-name]
  (first
    (map
      make-type-keyword
      (db/get-product-plan-by-name {:name
                                    (if (#{":test" ":dev" "test" "dev"} (str (:env config/env)))
                                      (str "Dev%" plan-name)
                                      (str "Prod%" plan-name))}))))

(defn validate-plans [plans]
  (cond
    (empty? plans) (throw (RuntimeException. (str "No product plans are defined in the db.")))
    :else
    plans))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; mount state components

(mount/defstate DB-PLANS
                :start (do
                         (prn "Config/ENV: " (:env config/env))

                         (prn "Query: " {:name-like
                                         (if (#{":test" ":dev" "test" "dev"} (str (:env config/env)))
                                           "Dev%"
                                           "Prod%")})
                         (prn "Got: " (map
                                        make-type-keyword
                                        (db/get-product-plans {:name-like
                                                               (if (#{":test" ":dev" "test" "dev"} (str (:env config/env)))
                                                                 "Dev%"
                                                                 "Prod%")})))
                         (validate-plans
                           (apply
                             schemas/must-be-valid!
                             schemas/PRODUCT-PLAN
                             (map
                               make-type-keyword
                               (db/get-product-plans {:name-like
                                                      (if (#{":test" ":dev" "test" "dev"} (str (:env config/env)))
                                                        "Dev%"
                                                        "Prod%")}))))))

(defn get-db-plans []
  DB-PLANS)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; public functions


(defn in-now-range
  "All subs that have not ended yet"
  [^long now sub]
  (or
    (not (:end-date sub))
    (<= now (long (t-coerce/to-long (:end-date sub))))))

(defn ensure-date [v]
  (if (number? v)
    (t-coerce/from-long v)
    v))

(defn insert-db-sub
  "Create a db subscription entry for a user with a plan-id and a stripe subscription id
   if start-date is null the current utc date is used"
  [user plan-id customer-plan-id stripe-id & {:keys [start-date end-date]}]
  {:pre [(:id user) (string? customer-plan-id) plan-id (string? stripe-id)]}

  (db/insert-subscription! {:user-id            (:id user)
                            :plan-id            plan-id
                            :customer-stripe-id customer-plan-id
                            :stripe-id          stripe-id
                            :start-date         (ensure-date (or start-date
                                                                 (time/now)))
                            :end-date           (when end-date
                                                  (ensure-date end-date))}))


(defn update-db-sub [sub & {:keys [plan stripe-id end-date]}]
  (let [record (cond-> {}
                       plan (assoc :plan_id (:id plan))
                       stripe-id (assoc :stripe_id stripe-id)
                       end-date (assoc :end_date end-date))]

    (prn "Update sub : " {:id      (:id sub)
                          :updates record})

    (db/update-subscription! {:id      (:id sub)
                              :updates record})))

(defn insert-db-sub* [PROVIDER customer-stripe-id user plan stripe-sub]
  {:pre [(:id plan)]}

  (let [start-date (or (billing-api/-subscription-start-date PROVIDER stripe-sub)
                       (time/now))]
    (insert-db-sub user
                   (:id plan)
                   customer-stripe-id
                   (billing-api/-subscription-id PROVIDER stripe-sub)
                   :start-date start-date)))

(defn is-upgrade? [{:keys [type]} plan]
  {:pre [type (:type plan)]}
  (or
    (= :free type)
    (and (= :basic type)
         (= :pro (:type plan)))))


(defn get-plan-by-type [plan-type]
  {:pre [(#{:free :start :basic :pro} plan-type)]}
  (let [plans (get-db-plans)]
    (first (filter #(plan-of-type? % plan-type) plans))))


(defn get-plan-by-name [plan-name]
  (plan-by-name plan-name))

(defn only-active-subs-sorted
  "Return subscriptions in date range and sorted desc by start-date
   opt for inmemory sorting and filter, the expected subs count is small (<100)"
  [subs]
  (let [now (System/currentTimeMillis)]
    (->> subs
         (filter #(in-now-range now %))
         (sort-by :start-date))))


(defn get-user-subs* [{:keys [id]}]
  (mapv make-type-keyword
        (db/get-user-subscriptions {:user-id id})))


(defn get-user-subs [user]

  (let [all-subs (get-user-subs* user)
        subs (mapv make-type-keyword
                   (only-active-subs-sorted
                     all-subs))]

    ;;ensure that the sub contains a valid product plan
    ;; if it fails here check the sql query
    (when (not-empty subs)
      (apply schemas/must-be-valid! schemas/PRODUCT-PLAN subs))

    subs))


(defn create-stripe-subscription
  "Creates a stripe subscription and return a Stripe Subscription"
  [PROVIDER customer-stripe-id plan-stripe-id]
  (billing-api/-create-subscription PROVIDER {:customer-stripe-id customer-stripe-id
                                              :plan-stripe-id     plan-stripe-id}))

(defn delete-stripe-subs [PROVIDER subs]
  (doseq [sub subs]
    (billing-api/-remove-subscription PROVIDER (:stripe-id sub))))

(defn delete-db-subs [subs]
  (doseq [sub subs]
    (db/delete-subscription! {:id (:id sub)})))


(defn do-first-time-sub [PROVIDER customer-stripe-id user plan]
  {:pre [customer-stripe-id user (:stripe-id plan)]}
  (prn "do-first-time-sub")

  (let [plan-stripe-id (:stripe-id plan)
        stripe-sub (create-stripe-subscription PROVIDER customer-stripe-id plan-stripe-id)]

    (prn "stripe-sub: " {:stripe-sub stripe-sub})

    (insert-db-sub* PROVIDER customer-stripe-id user plan stripe-sub)))


(defn do-upgrade-sub [PROVIDER customer-stripe-id user subs new-plan]
  {:pre [(first subs)]}
  (prn "do-upgrade-sub")

  (let [sub (first subs)
        stripe-sub (billing-api/-upgrade-stripe-sub PROVIDER user {:customer-stripe-id customer-stripe-id
                                                                   :sub                sub
                                                                   :plan               new-plan})
        stripe-sub-id (billing-api/-subscription-id PROVIDER stripe-sub)

        _ (update-db-sub sub :plan new-plan :stripe-id stripe-sub-id)]

    (delete-stripe-subs PROVIDER (rest subs))
    (delete-db-subs (rest subs))

    stripe-sub-id))


(defn do-downgrade-sub [PROVIDER customer-stripe-id user subs new-plan]
  (prn "do-downgrade-sub")
  ;;{:stripe-end-date 1537598792000, :epoc 1537598792}
  (let [sub (first subs)
        subs-to-delete (vec (rest subs))
        end-sub (billing-api/-end-subscription PROVIDER (:stripe-id sub))
        end-date (t-coerce/from-long (billing-api/-subscription-end-date PROVIDER end-sub))

        _ (update-db-sub sub :end-date end-date)

        start-date end-date


        ;;plan-stripe-id, customer-stripe-id start-date
        new-sub (billing-api/-create-subscription PROVIDER {:plan-stripe-id     (:stripe-id new-plan)
                                                            :customer-stripe-id customer-stripe-id
                                                            :start-date         start-date})

        ]


    (insert-db-sub* PROVIDER customer-stripe-id user new-plan new-sub)

    (delete-stripe-subs PROVIDER subs-to-delete)
    (delete-db-subs subs-to-delete)

    {:end-date   end-date
     :start-date start-date
     :new-sub    new-sub}))

(defn ensure-plan [PROVIDER user customer-stripe-id plan-name]
  {:pre [(:id user) (:plan user) customer-stripe-id (string? plan-name)]}

  (let [subs (get-user-subs user)
        user-plan (:plan user)
        new-plan (or
                   (get-plan-by-name plan-name)
                   (when (StringUtils/startsWithIgnoreCase (str plan-name) "free")
                     (get-plan-by-name "free")))]

    (prn "ensure-plan " {:plan-by-name plan-name
                         :user-plan user-plan :new-plan new-plan :user-subs subs})
    (cond
      (not user-plan) (throw (RuntimeException. (str "A user must have a plan attribute")))
      (not new-plan) (throw (RuntimeException. (str "Cannot upgrade to a non existent plan " plan-name)))
      (empty? subs) (do-first-time-sub PROVIDER customer-stripe-id user new-plan)
      (is-upgrade? user-plan new-plan) (do-upgrade-sub PROVIDER customer-stripe-id user subs new-plan)
      :else (do-downgrade-sub PROVIDER customer-stripe-id user subs new-plan))))

(defn get-active-user-sub-plan
  "
   If plan-override-type is specified we use that instead of any other subscription made
   This is an easy way to upgrade a user
  "
  [user]
  (let [plan-type-override (:plan-override-type user)
        sub (or
              (and plan-type-override (get-plan-by-type (keyword plan-type-override)))

              (first (get-user-subs user))

              (get-plan-by-type :free))]

    (when (empty? sub)
      (throw (RuntimeException. (str "Internal Error: No subscription wsa returned"))))

    ;(prn "################ SUB: " sub)
    sub))
