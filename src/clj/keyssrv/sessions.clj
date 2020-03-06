(ns
  ^{:doc "All code specific to sessions"}
  keyssrv.sessions
  (:require [mount.core :as mount]
            [keyssrv.config :as config]
            [keyssrv.tokens.core :as tokens]
            [ring.middleware.session.store :as ring-session]
            [clj-uuid :as uuid]
            [keyssrv.utils :as utils]
            [codex.core :as codex]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; redis session code


(defn create-redis-session-store [token-store {:keys [session-store]}]
  {:pre [token-store session-store]}

  (let [ttl (utils/ensure-int (or (:ttl-seconds session-store) 3600))
        prefix "webs_"
        make-key (fn [k] (utils/utf-bytes (str prefix k)))
        make-token-id (fn [k] (codex/expand-key :sha128+hmac256 k))]

    ;;important for security
    ;; The session-id is used as the encryption key for the session
    ;; the derived key from the session-id is used as the token id
    ;;  the reason is that if someone gets holds of the redis data they cannot
    ;;  see the encryption key, if we used the same token id as the encryption key that would defeat the encryption.
    ;;
    ;; the session id is stored by the user in his/her secure https cookie and only available to the user.
    ;; reducing the risk of an offline attack.
    ;; remember that the token store itself ads a secret pepper to the session id.
  (reify ring-session/SessionStore

    (read-session [_ key]
      ;(prn "Read session: " key)
      (let [k (make-key key)]
        (tokens/-get-token token-store k (make-token-id k))))

    (write-session [_ key data]
      (let [key' (or key (str (uuid/v4)))
            k (make-key key')]

        ;(prn "Writing session key: " (if key {:exist key} {:new key'}) " value " {:data data} " END")
        (tokens/-set-token
          token-store
          k
          (make-token-id k)
          data
          ttl)

        key'))

    (delete-session [_ key]
      (tokens/-delete-token token-store (make-token-id (make-key key)))))))


;; create a session store backed by the default token store
(mount/defstate DefaultSessionStore
                :start (create-redis-session-store (tokens/token-store) config/env))

(defn session-store []
  DefaultSessionStore)

(defn recreate-session
  "
  Create a new token and then add it to the session store, and to the req :session/key
  The session is added to :session
  "
  [req session]
  (let [k (ring-session/write-session (session-store) nil session)]
    (-> req
      (assoc :session/key k)
      (assoc :session session))))

(defn update-session [req session]
  (when-let [session-key (:session/key req)]
    (ring-session/write-session (session-store) session-key session)))

(defn remove-session [req]
  (when-let [session-key (:session/key req)]
    (ring-session/delete-session (session-store) session-key)))

