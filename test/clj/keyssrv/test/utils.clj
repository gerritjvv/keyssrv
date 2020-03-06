(ns keyssrv.test.utils
  (:require
    [keyssrv.config :as config]
    [keyssrv.db.core :as db]

    [keyssrv.groups.core :as groups]
    [keyssrv.users.registration :as reg]
    [keyssrv.secret.keys :as gkeys]
    [keyssrv.pwd.checks :as pwd-checks]
    [keyssrv.billing.core :as billing]
    [keyssrv.billing.plans :as plans]
    [keyssrv.sessions :as sessions]
    [keyssrv.routes.appkeys :as appkeys]
    ;;this namespace is in env/dev/clj
    [keyssrv.product :as product-utils]

    [keyssrv.core]
    [keyssrv.billing.plans :as plans]

    [mount.core :as mount]
    [clojure.test :refer :all]
    [luminus-migrations.core :as migrations]
    [keyssrv.notification.notify :as notify]
    [cheshire.core :as json]
    [keyssrv.secret.keys :as keys])
  (:import (java.util UUID Collection)
           (java.net ServerSocket)
           (java.io IOException InputStream File)
           (java.util.concurrent TimeoutException)
           (keyssrv.util CryptoHelper)))

(defn setup [f]
  (mount/start #'config/env
               #'db/*db*)

  (migrations/migrate ["migrate"] (select-keys config/env [:database-url]))
  (product-utils/setup-stripe-products)

  (mount/start-with {#'billing/PROVIDER (billing/test-provider)
                     #'notify/NOTIFIER  (notify/test-notifier)})

  (mount/start #'gkeys/SYSTEM_KEY
               #'pwd-checks/COMMON-PWDS
               #'sessions/DefaultSessionStore
               #'plans/DB-PLANS
               #'keyssrv.core/http-server)


  (mount/start #'keyssrv.tokens.core/DefaultTokenStore)
  ;
  ;(mount/start-with {#'billing/PROVIDER (billing/test-provider)})

  (mount/start)

  (prn "APP STARTED FOR TESTING")

  (f))

(use-fixtures :once setup)


(defn unique-str []
  (str (UUID/randomUUID)))

(defn get-user-by-id [id]
  (reg/get-user-by-user-id id))

(defn create-user-app-key
  "Create app key in the db and returns {:key-id :key-secret :date-expire}
   date-expire is 1 year from now
  "
  [user]
  (appkeys/create-user-app-key user nil))

(defn create-user
  "Create random user and return the whole user record including the :id field"
  [& {:keys [user-name email plan-type]}]
  (let [name' (or user-name (unique-str))
        email' (or email (str (unique-str) "@test.com"))
        password "123456"
        salt (CryptoHelper/genKey 16)
        {:keys [pass-hash pass-info]} (gkeys/password-hash {:salt salt} password)
        enc-key (gkeys/gen-key)
        enc-key' (gkeys/encrypt pass-hash enc-key)]

    (assoc
      (reg/create-user name' email' (keys/encrypt pass-hash pass-hash) enc-key' pass-info 0 0) ;; here we need to safe the encrypted enc key
      :enc-key enc-key ;; we need to return the decrypted enc key
      :password password
      :has-mfa false
      :plan (plans/get-plan-by-type (or plan-type
                                        :free)))))

(defn create-group
  "Create random group and return the group id"
  [user & {:keys [name admin]}]
  (let [name' (or name (str (UUID/randomUUID)))
        admin' (or admin true)
        group (groups/create-pass-group user name' admin')]

    group))



(defn session [user group-id]
  {:session {:identity (assoc user
                         :ts (System/currentTimeMillis))}
   :params {:group-id group-id}})


(defn parse-ui-errors [resp]
  (when (instance? Throwable resp)
    (throw resp))

  (get-in resp [:flash :errors]))

(defn test-no-ui-errors [resp]
  (is (nil? (parse-ui-errors resp))))


(defn port-open?
  "return true if the port is in use"
  [port]
  {:pre [(integer? port)]}
  (try
    ;;if the port is bound i.e open we cannot bind to it with a server socket
    (.close (ServerSocket. (int port)))
    (catch IOException _
      true)))

(defn wait-for-port-open!
  "Wait for timeout-ms millis if the port is not open a TimeoutException is thrown
  nil is always returned"
  [timeout-ms port]
  (let [start-time (System/currentTimeMillis)]
    (loop []
      (when-not (port-open? port)
        (when (> (- (System/currentTimeMillis) start-time) timeout-ms)
          (throw (TimeoutException.)))
        (Thread/sleep 500)
        (recur)))))

(defn body-slurp [body]
  (cond
    (instance? File body) (json/parse-string (slurp body) keyword)
    (instance? InputStream body) (json/parse-string (slurp body) keyword)
    (instance? Collection body) body
    (instance? String body) (json/parse-string body keyword)
    :else body))