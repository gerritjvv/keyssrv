(ns
  ^{:doc "setup stripe product and plan entries for dev"}
  keyssrv.product
  (:require [keyssrv.db.core :as db]
            [clojure.tools.logging :refer [error]])
  (:import (org.apache.commons.lang3 StringUtils)))


;;; The stripe ids here are for the stripe test environment.
;;; Check that the correct STRIPE_SK is defined in .env-prod
;;; and that the plans and products are created

(def PROD_DEV_LIMITS {:pro
                      {:curr         "USD"
                       :max-users    1000
                       :max-vaults   1000
                       :max-secrets  1000
                       :max-logins   1000
                       :max-envs     1000
                       :max-certs    1000
                       :max-snippets 1000}

                      :basic
                      { :curr         "USD"
                       :max-users    25
                       :max-vaults   10
                       :max-secrets  100
                       :max-logins   100
                       :max-envs     10
                       :max-certs    10
                       :max-snippets 10
                       }

                      :start
                      { :curr         "USD"
                       :max-users    5
                       :max-vaults   5
                       :max-secrets  10
                       :max-logins   100
                       :max-envs     10
                       :max-certs    10
                       :max-snippets 10
                       }})

(def PRODUCTS [{:name      "DEV-PKHub"
                :stripe-id "prod_E76jrfrJtIjm8N"
                :plans     [

                            (merge
                              {:name      "Dev-PRO-MONTH"
                               :type      (name :pro)
                               :stripe-id "plan_EYCdVIATsVsuXa"
                               :cost      5.00

                               ;; unlimited account in theory but we must add some upper limits to avoid
                               ;; unfair usage, if users want the cap raised they should contact us

                               }
                              (:pro PROD_DEV_LIMITS))

                            (merge
                              {:name      "Dev-PRO-YEAR"
                               :type      (name :pro)
                               :stripe-id "plan_EYCY5crzM48d8r"
                               :cost      48.00

                               ;; unlimited account in theory but we must add some upper limits to avoid
                               ;; unfair usage, if users want the cap raised they should contact us

                               }
                              (:pro PROD_DEV_LIMITS))

                            (merge
                              {:name      "Dev-BASIC-MONTH"
                               :type      (name :basic)
                               :stripe-id "plan_EYCcxoTskngzM3"
                               :cost      3.00

                               }
                              (:basic PROD_DEV_LIMITS))

                            (merge
                              {:name      "Dev-BASIC-YEAR"
                               :type      (name :basic)
                               :stripe-id "plan_EYCbcd2iNQWk7o"
                               :cost      24.00

                               }
                              (:basic PROD_DEV_LIMITS))

                            (merge
                              {:name      "Dev-START-MONTH"
                               :type      (name :start)
                               :stripe-id "plan_FaLJEpXUpo0N8e"
                               :cost      5.00

                               }
                              (:start PROD_DEV_LIMITS))

                            (merge
                              {:name      "Dev-START-YEAR"
                               :type      (name :start)
                               :stripe-id "plan_FaLKGvB8FlVf5Y"
                               :cost      5.00


                               }
                              (:start PROD_DEV_LIMITS))

                            {:name         "Dev-FREE-MONTH"
                             :type         (name :free)
                             :stripe-id    "plan_DQx2Mg6c4t4m4o"
                             :cost         0
                             :curr         "USD"
                             :max-users    3
                             :max-vaults   2
                             :max-secrets  10
                             :max-logins   10
                             :max-envs     2
                             :max-certs    2
                             :max-snippets 2}

                            ]}])



(defn product-exists? [prod]
  (first (db/get-products {:name-like (StringUtils/lowerCase (str (:name prod)))})))

(defn product-plan-exists? [plan]
  (first (db/get-product-plans {:name-like (:name plan)})))

(defn ensure-plan! [product-id plan]
  (prn "Ensure Plan exists: " (:name plan))
  (let [res (or
              (product-plan-exists? plan)
              (db/insert-product-plan! (assoc plan :product-id product-id)))]

    res))


(defn insert-product! [prod]
  (prn "Inserting product: " prod)
  (db/insert-product! prod))

(defn ensure-product! [prod]
  (try
    (let [prod-record (or
                        (product-exists? prod)
                        (insert-product! prod))]
      (doseq [plan (:plans prod)]
        (ensure-plan! (:id prod-record) plan)))
    (catch Exception e
      (error e))))

(defn setup-stripe-products []
  (doseq [prod PRODUCTS]
    (ensure-product! prod)))