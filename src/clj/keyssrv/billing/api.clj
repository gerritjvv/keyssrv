(ns
  ^{:doc "Abstracts the billing protocol"}
  keyssrv.billing.api)



(defprotocol IProvider
  (-create-customer [this card-src-id record])
  (-update-customer-source [this customer-id card-src-id])

  (-get-customer [this id])
  (-remove-customer [this id])

  (-end-subscription [this id])
  (-subscription-end-date [this sub] "subscription end date in millis, may be null")

  (-create-subscription [this record] "record => plan-stripe-id, customer-stripe-id start-date")
  (-subscription-id [this record] "gets the provider id")
  (-subscription-start-date [this record] "get the subscription start date in millis")

  (-upgrade-stripe-sub [this user data] "data keys => customer-stripe-id :plan {:id :cost :stripe-id} }
                                         returns the upgraded subscription")


  (-get-subscription [this id])
  (-remove-subscription [this id])
  (-create-payment-src [this user card-token])
  (-get-payment-src [this card-token])
  (-remove-payment-src [this card-token]))