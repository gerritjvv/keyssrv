(ns
  ^{:doc
    "Everything to do with user login"}
  keyssrv.users.login
  (:require [keyssrv.config :as config]
            [struct.core :as st]
            [keyssrv.schemas.core :as schemas]))
;;test

(defn as-long [v]
  (when v
    (if (string? v)
      (Long/valueOf (str v))
      (long v))))

(defn not-expired? [ts]
  (let [diff (- (System/currentTimeMillis) (long ts))]

    (<= diff (* (as-long (or
                           (get-in config/env [:session-store :max-login-seconds])
                           86400))
                1000))))


(defn middleware-user-logged-in? [request]

  (when-let [user (:identity (:session request))]
    (let [schema-errors (first (st/validate user schemas/USER-SESSION-IDENTITY))]
      (cond
        (or (not (:ts user))
            ((complement not-expired?) (:ts user))) {:user nil :errors "Session Expired"}

        (not-empty schema-errors) {:user nil :errors schema-errors}
        :else
        {:user user :errors nil}))))

(defn user-logged-in?
  "Returns the user identity record {:email <email>}, if logged in"
  [request]
  (:user (middleware-user-logged-in? request)))

