(ns keyssrv.test.billing.plans-test
  (:require
    [keyssrv.billing.core :as billing]
    [keyssrv.billing.plans :as billing-plans]
    [keyssrv.routes.settings.plans :as route-plans]
    [clj-time.core :as time]
    [clojure.test :refer :all]
    [keyssrv.test.utils :as test-utils]))

(defn init-records []
  (let [card-src-id (str (System/currentTimeMillis))
        user (test-utils/create-user)]

    {:card-src-id card-src-id
     :user        user
     :customer    (billing/ensure-customer card-src-id user)
     :payment-src (billing/ensure-payment-src card-src-id user)}
    ))

(defn setup [f]
  (test-utils/setup f))

(use-fixtures :once setup)


(defn session-with-params [user m]
  (assoc
    (test-utils/session user -1)
    :params m))

(defn send-ui-upgrade [user plan-type]
  (test-utils/test-no-ui-errors
    (route-plans/update-account (session-with-params user
                                                     {:action "update"
                                                      :plan   (name plan-type)}))))


(deftest test-is-upgrade
  (is (billing-plans/is-upgrade? {:type :basic} {:type :pro}))
  (is (not (billing-plans/is-upgrade? {:type :pro} {:type :basic})))
  (is (not (billing-plans/is-upgrade? {:type :pro} {:type :free})))
  (is (not (billing-plans/is-upgrade? {:type :basic} {:type :basic})))

  (is (billing-plans/is-upgrade? {:type :free} {:type :pro}))
  (is (billing-plans/is-upgrade? {:type :free} {:type :basic})))

(deftest test-downgrade-upgrade-cycles

  (let [{:keys [user]} (init-records)
        _  (send-ui-upgrade user :pro)
        sub (first (billing-plans/get-user-subs user))
        user' (assoc user :plan sub)
        plan-paths (repeatedly 1 #(rand-nth [:free :basic :pro]))]

    ;;upgrade

    (reduce (fn [[current-user prev-plan] new-plan-type]
              (let [sub (first (billing-plans/get-user-subs current-user))

                    new-user (assoc current-user :plan sub)

                    _ (send-ui-upgrade new-user new-plan-type)

                    subs' (billing-plans/get-user-subs new-user)

                    upgrade? (billing-plans/is-upgrade? prev-plan {:type new-plan-type})
                    ]

                (if upgrade?
                  (is (= (count subs') 1))
                  (is (= (count subs') 2)))

                [new-user (first subs')]))
            [user' :free]
            plan-paths)))

(deftest test-downgrade-pro-basic
  (let [{:keys [user]} (init-records)
        _  (send-ui-upgrade user :pro)
        sub (first (billing-plans/get-user-subs user))
        user' (assoc user :plan sub)]

    ;;upgrade


    ;;upgrade to pro
    (send-ui-upgrade user' :basic)

    (let [[frst snd :as subs] (billing-plans/get-user-subs user')
          frst-end-date (:end-date frst)
          snd-start-date (:start-date snd)]

      (is
        (= 2 (count subs)))

      (is
        (= :pro (:type frst)))
      (is
        (= :basic (:type snd)))

      (is frst-end-date)

      (is (time/equal? frst-end-date snd-start-date)))))

(deftest test-upgrade-basic-to-pro
  (let [{:keys [user]} (init-records)]

    ;;upgrade
    (send-ui-upgrade user :basic)

    ;;upgrade to pro
    (send-ui-upgrade user :pro)

    (let [subs (billing-plans/get-user-subs user)]
      (is
        (= 1 (count subs)))

      (is
        (= :pro (:type (first subs)))))))

(deftest test-create-plan
  (let [{:keys [user]} (init-records)]
    (send-ui-upgrade user :basic)

    (let [subs (billing-plans/get-user-subs user)]
      (prn "User subscriptions: " subs)
      (is
        (pos? (count subs))))))

