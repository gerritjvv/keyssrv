(ns
  ^{:doc "Password groups"}
  keyssrv.routes.settings
  (:require [keyssrv.routes.settings.account :as settings-account]
            [keyssrv.routes.settings.payment-src :as settings-paymentsrc]
            [keyssrv.routes.settings.plans :as settings-plans]
            [keyssrv.routes.settings.reset-tokens :as settings-tokens]
            [keyssrv.routes.settings.mfa :as settings-mfa]

            [clojure.tools.logging :refer [info error]]))

(declare view-settings-items)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;; private functions


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;; public functions


;;;;;;;;;;;;;;; Settings Sub Items

(defn view-settings-items
  "View settings sub items"
  [view request]
  (cond
    (= view "account") (settings-account/view request)
    (= view "billing") (settings-paymentsrc/view request)
    (= view "plans") (settings-plans/view request)
    (= view "tokens") (settings-tokens/view request)
    (= view "security") (settings-tokens/view-security request)
    (= view "mfa") (settings-mfa/view request)

    :else
    (throw (RuntimeException. (str "View: " view " not recognised")))))

(defn create-or-delete-settings-items
  "CRUD for settings sub items"
  [view request]

  (cond
    (= view "account") (settings-account/update-account request)
    (= view "billing") (settings-paymentsrc/update-account request)
    (= view "plans") (settings-plans/update-account request)
    (= view "tokens") (settings-tokens/update-account request)
    (= view "mfa") (settings-mfa/update-account request)

    ;(= view "secrets") (group-secrets/add-or-remove group-id )
    ;(= view "logins") (group-logins/add-or-remove group-id request)
    ;(= view "snippets") (group-snippets/add-or-remove group-id request)
    ;(= view "certs") (group-certs/add-or-remove group-id request)
    ;(= view "envs") (group-envs/add-or-remove group-id request)


    :else
    (throw (RuntimeException. (str "View: " view " not recognised")))))


