(ns keyssrv.billing.core
    (:require
      [keyssrv.db.core :as db]
      [keyssrv.config :as config]
      [keyssrv.billing.plans :as plans]
      [keyssrv.billing.api :as billing-api]
      [mount.core :as mount]
      [clj-time.core :as time]
      [clj-time.coerce :as t-coerce])
    (:import (com.stripe Stripe)
      (com.stripe.model Customer Source Subscription SubscriptionItem)))



(defn remove-record [a id]
      (swap! a dissoc id))

(defn add-record
      ([a record]
        (add-record a record (str (System/nanoTime))))
      ([a record id]
        (swap! a assoc id record)
        id))

(defn test-provider []
      (let [customers (atom {})
            subs (atom {})
            cards (atom {})]

           (reify billing-api/IProvider
                  (-create-customer [_ card-src-id record]
                                    (let [cust {:id     (str (System/nanoTime))
                                                :src-id card-src-id}]
                                         (add-record customers cust)
                                         cust))

                  (-update-customer-source [_ customer-id card-src-id]
                                           )


                  (-upgrade-stripe-sub [_ _ {:keys [customer-stripe-id sub plan]}]

                                       (if-let [saved-sub (get @subs (:stripe-id sub))]
                                               (let [record (assoc saved-sub
                                                                   :type (:type plan)
                                                                   :plan-id (:id plan)
                                                                   :customer-stripe-id customer-stripe-id
                                                                   :plan plan)]
                                                    (add-record subs record (:id sub))
                                                    record)
                                               (throw (RuntimeException. (str "Subscription: " sub " does not exist")))))

                  (-get-customer [_ id]
                                 (get @customers id))

                  (-remove-customer [_ id]
                                    (remove-record customers id))

                  (-create-subscription [_ record]
                                        (let [id (str (System/nanoTime))
                                              record' (cond-> (assoc record :id id)
                                                              (:start-date record) (assoc :start-date (:start-date record))
                                                              (not (:start-date record)) (assoc :start-date (System/currentTimeMillis)))]


                                             (add-record subs record' id)
                                             record'))

                  (-get-subscription [_ id]
                                     (get @subs id))

                  (-subscription-end-date [_ sub]
                                          (:end-date sub))

                  (-end-subscription [_ id]
                                     (if-let [saved-sub (get @subs id)]
                                             (let [record (assoc saved-sub
                                                                 :end-date (t-coerce/to-long (time/plus (time/now) (time/days 1))))]
                                                  (add-record subs record id)
                                                  record)
                                             (throw (RuntimeException. (str "Subscription: " id " does not exist")))))

                  (-remove-subscription [_ id]
                                        (remove-record subs id))

                  (-subscription-id [_ sub]
                                    (:id sub))

                  (-subscription-start-date [_ sub]
                                            (:start-date sub))

                  (-create-payment-src [_ _ card-token]
                                       (add-record cards card-token))

                  (-get-payment-src [_ card-token]
                                    (get @cards card-token))

                  (-remove-payment-src [_ card-token]
                                       (remove-record cards card-token)))))


(defn get-db-customer-rel-id
      "Returns the customer stripe id"
      [user]
      {:pre [(:id user)]}
      (let [record (db/get-user-customer {:user-id (:id user)})]
           (when (not-empty record)
                 (:stripe-id record))))


(defn remove-db-customer-rel [user customer-id]
      {:pre [(:id user) customer-id]}
      (db/delete-user-customer-rel! {:user-id   (:id user)
                                     :stripe-id customer-id}))

(defn get-db-payment-src-rel-id [user]
      {:pre [(:id user)]}
      (let [record (db/get-user-payment-src {:user-id (:id user)})]
           (when (not-empty record)
                 (:stripe-id record))))

(defn get-db-payment-src-rel [user]
      {:pre [(:id user)]}
      (let [record (db/get-user-payment-src {:user-id (:id user)})]
           (when (not-empty record)
                 record)))

(defn remove-db-payment-src-rel [user payment-id]
      {:pre [(:id user) payment-id]}
      (db/delete-user-payment-rel! {:user-id   (:id user)
                                    :stripe-id payment-id}))

(defn convert-unix-to-ms [v]
      (*
        (long (or v 0)) 1000))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;; public functions and mount states

;;;; TESTING: see keyssrv.test.utils, the kuser loads the stripe provider

(mount/defstate PROVIDER
                :start (let [pk (:stripe-pk config/env)
                             sk (:stripe-sk config/env)]

                            (when-not (and pk sk)
                                      (throw (RuntimeException. (str "STRIPE_PK and STRIPE_SK for stripe must be defined"))))


                            (set! Stripe/apiKey sk)
                            (reify billing-api/IProvider
                                   (-create-customer [_ card-src-id {:keys [email]}]
                                                     (when-not card-src-id
                                                               (throw (RuntimeException. (str "Card source id must be specified"))))

                                                     ;;see https://stripe.com/docs/api/java#create_customer
                                                     ;; note do not just add any attributes
                                                     (when-let [customer (Customer/create {"email"  email
                                                                                           "source" card-src-id})]
                                                               {:id (.getId customer)}))

                                   (-update-customer-source [_ customer-id card-src-id]

                                                            (when-let [customer (Customer/retrieve (str customer-id))]

                                                                      ;;remove any sources from the customer
                                                                      (when-let [sources (.getSources customer)]
                                                                                (doseq [source (iterator-seq (.iterator (.autoPagingIterable sources)))]
                                                                                       (when (instance? Source source)
                                                                                             (.detach ^Source source))))

                                                                      ;;attach new source
                                                                      (when card-src-id
                                                                            (.create (.getSources customer) {"source" card-src-id}))))

                                   (-get-customer [_ id]
                                                  (when-let [customer (Customer/retrieve (str id))]
                                                            {:id (.getId customer)}))

                                   (-remove-customer [_ id]
                                                     (.delete (Customer/retrieve (str id))))

                                   (-upgrade-stripe-sub [_ _ {:keys [customer-stripe-id sub plan]}]
                                                        ;;see https://stripe.com/docs/billing/subscriptions/upgrading-downgrading

                                                        (when-not (and customer-stripe-id
                                                                       (:stripe-id sub)
                                                                       (:stripe-id plan))
                                                                  (throw (RuntimeException. (str "Sub plan must have a stripe-id defined"))))

                                                        (if-let [stripe-sub (Subscription/retrieve (:stripe-id sub))]
                                                                (.update ^Subscription stripe-sub
                                                                         {"cancel_at_period_end" false
                                                                          "prorate"              true
                                                                          "items"                {"0" {"id"   (.getId ^SubscriptionItem (.get (.getData (.getSubscriptionItems stripe-sub)) (int 0)))
                                                                                                       "plan" (:stripe-id plan)}}})

                                                                (throw (RuntimeException. (str "Subscription not found")))))

                                   (-create-subscription [_ {:keys [customer-stripe-id plan-stripe-id start-date]}]

                                                         (when-not plan-stripe-id
                                                                   (throw (RuntimeException. (str "plan-stripe-id cannot be null"))))
                                                         (when-not customer-stripe-id
                                                                   (throw (RuntimeException. (str "customer-stripe-id cannot be null"))))

                                                         (let [record {"customer" customer-stripe-id
                                                                       "items"    {"0" {"plan" plan-stripe-id}}}

                                                               record' (cond-> record

                                                                               start-date (assoc "billing_cycle_anchor" (t-coerce/to-epoch start-date)))
                                                               sub (Subscription/create record')]


                                                              (prn ">>>>>>>>> Create subscription: " {:end-period   (.getCurrentPeriodEnd sub)
                                                                                                      :start-period (.getCurrentPeriodStart sub)
                                                                                                      :start-anchor (.getBillingCycleAnchor sub)
                                                                                                      })
                                                              sub))

                                   (-subscription-id [_ sub]
                                                     (.getId ^Subscription sub))

                                   (-subscription-start-date [_ sub]
                                                             (convert-unix-to-ms (.getBillingCycleAnchor ^Subscription sub)))


                                   (-get-subscription [_ id]
                                                      ;(get @subs id)
                                                      (Subscription/retrieve (str id)))

                                   (-end-subscription [_ id]
                                                      (when-let [sub (Subscription/retrieve (str id))]
                                                                (let [sub' (.update sub {"cancel_at_period_end" true})]
                                                                     (prn "Ending subcription at end_period: " (.getCurrentPeriodEnd sub'))

                                                                     sub')))

                                   (-subscription-end-date [_ sub]
                                                           (convert-unix-to-ms (.getCurrentPeriodEnd ^Subscription sub)))

                                   (-remove-subscription [_ id]
                                                         (when-let [sub (Subscription/retrieve (str id))]
                                                                   (.cancel ^Subscription sub {})))

                                   (-create-payment-src [_ _ card-src-id]
                                                        ;;should never be called, the src-id comes from the client side code always
                                                        {:id card-src-id})

                                   (-get-payment-src [_ src-id]
                                                     (when-let [src (Source/retrieve (str src-id))]
                                                               {:id (.getId ^Source src)}))

                                   (-remove-payment-src [_ src-id]
                                                        (.delete (Source/retrieve (str src-id)))))))


;;;;;;;;;;;;;;;;;;;;;;;;;; payment-src

(defn create-payment-src
      "Create a payment-src using the PROVIDER, then
       add the payment-src user_payment-src and return {:stripe-id user-id}

       Add logic: Does payment-src relation exist, if so does payment-src exist in stripe
      "
      [card-src-id {:keys [id] :as user}]
      {:pre [id card-src-id]}
      (let [record {:user-id     id
                    :stripe-id   card-src-id
                    :card-name   (:card-name user)
                    :card-exp    (:card-exp user)
                    :card-last-4 (:card-last-4 user)}]

           (when (empty? (billing-api/-create-payment-src PROVIDER user card-src-id))
                 (throw (RuntimeException. (str "The PROVIDER " PROVIDER " returned a nil id"))))


           (db/insert-payment-src! record)

           {:id card-src-id}))

(defn ensure-payment-src
      "Ensures that a payment-src entry exists and returns the payment-src id"
      [card-src-id {:keys [id] :as user}]
      {:pre [id]}
      (let [payment-src-id (get-db-payment-src-rel-id user)]
           (if-not payment-src-id
                   (create-payment-src card-src-id user)
                   (let [payment-src (billing-api/-get-payment-src
                                       PROVIDER
                                       payment-src-id)]
                        (if (empty? payment-src)
                          (throw
                            (RuntimeException.
                              (str
                                "Payment source does not exist in stripe "
                                payment-src-id)))
                          payment-src)))))



(defn remove-payment-src [user]
      (let [payment-src-id (get-db-payment-src-rel-id user)]
           (when payment-src-id
                 (billing-api/-remove-payment-src PROVIDER payment-src-id)
                 (remove-db-payment-src-rel user payment-src-id))))

;;;;;;;;;;;;;;;;;;;;;;;;;; customer


(defn create-customer
      "Create a customer using the PROVIDER, then
       add the customer user_customer and return {:stripe-id user-id}

       Add logic: Does custeomer relation exist, if so does customer exist in stripe
      "
      [card-src-id {:keys [id email user-name] :as user}]
      {:pre [id email user-name card-src-id]}
      (let [cust (billing-api/-create-customer PROVIDER card-src-id user)]

           (when (empty? cust)
                 (throw (RuntimeException. (str "The PROVIDER " PROVIDER " returned a nil id"))))


           (db/insert-customer! {:user-id   id
                                 :stripe-id (:id cust)})
           cust))


(defn update-customer-id [user customer-id new-customer-id]
      {:pre [(:id user) customer-id new-customer-id]}
      (db/update-customer-id! {:user-id       (:id user)
                               :stripe-id     customer-id
                               :new-stripe-id new-customer-id}))

(defn ensure-customer
      "Ensures that a customer entry exists and that the card-src-id is attached to it,
       and returns the customer id"
      [card-src-id user]
      (let [customer-id (get-db-customer-rel-id user)]
           (if-not customer-id
                   (create-customer card-src-id user)
                   (let [cust (billing-api/-get-customer PROVIDER customer-id)]
                        (if (empty? cust)
                          (update-customer-id
                            user
                            customer-id
                            (billing-api/-create-customer PROVIDER card-src-id user))
                          (do
                            (billing-api/-update-customer-source
                              PROVIDER
                              customer-id
                              card-src-id)
                            cust))))))



(defn remove-customer [user]
      (let [customer-id (get-db-customer-rel-id user)]
           (when customer-id
                 (billing-api/-remove-customer PROVIDER customer-id)
                 (remove-db-customer-rel user customer-id))))


(defn ensure-plan [user customer-stripe-id plan-name]
      {:pre [(string? plan-name)]}
      (plans/ensure-plan PROVIDER user customer-stripe-id plan-name))