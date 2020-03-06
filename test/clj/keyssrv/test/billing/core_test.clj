(ns keyssrv.test.billing.core-test
  (:require
    [keyssrv.billing.core :as billing]
    [clojure.test :refer :all]
    [keyssrv.test.utils :as test-utils]))

(defn setup [f]
  (test-utils/setup f))

(use-fixtures :once setup)

(deftest test-create-customer-no-stripe-id
  (let [user (test-utils/create-user)
        card-src-id (str (System/currentTimeMillis))
        customer-id (billing/ensure-customer card-src-id user)
        _ (billing/remove-customer user)
        customer-id' (billing/ensure-customer card-src-id user)]

    (is customer-id)
    (is customer-id')
    (is (not= customer-id customer-id'))

    [user
     customer-id
     customer-id']))

(deftest test-create-customer
  (let [user (test-utils/create-user)
        card-src-id (str (System/currentTimeMillis))
        customer-id (billing/ensure-customer card-src-id user)]

    (is customer-id)))

(deftest test-create-payment-no-stripe-id
  (let [user (test-utils/create-user)
        card-src-id (str (System/currentTimeMillis))
        card-record  (billing/ensure-payment-src card-src-id user)
        _ (billing/remove-db-payment-src-rel user (:id card-record))
        card-src-id' (billing/ensure-payment-src card-src-id user)]

    (is card-src-id)
    (is card-src-id')
    (is (not= card-src-id card-src-id'))))

(deftest test-create-payment-src
  (let [user (test-utils/create-user)
        card-src-id (str (System/currentTimeMillis))
        card-id (billing/ensure-payment-src card-src-id user)]

    (is card-id)))