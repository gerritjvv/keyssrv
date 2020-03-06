(ns
  ^{:doc "User account reset tokens"}
  keyssrv.routes.settings.reset-tokens
  (:require [ring.util.http-response :as response]
            [keyssrv.secret.keys :as s-keys]
            [keyssrv.users.auth :as auth]
            [keyssrv.tokens.core :as tokens-core]
            [taoensso.nippy :as nippy]
            [keyssrv.layout :as layout]
            [keyssrv.db.core :as db]
            [keyssrv.secret.keys :as keys]
            [clojure.tools.logging :refer [error]]
            [keyssrv.utils :as utils]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; private functions

(defn get-reset-token [user token-key]
  (let [hash-vals (db/get-user-reset-codes {:user-id (:id user)})]

    (loop [[token-record & hash-vals'] hash-vals]

      (let [code-hash (:code-hash token-record)]
        (when code-hash
          (if (keys/check-api-key-against-hash {} token-key code-hash)
            token-record
            (recur (rest hash-vals'))))))))

(defn delete-reset-token [token-id]
  (db/delete-user-reset-code! {:id token-id}))


(defn reset-token-count [user]
  (:count (db/select-user-reset-code-count {:user-id (:id user)})))

(defn get-reset-token-and-delete
  "Find a reset token that has a code-hash that matches the hash of token-key
   then removes the token from the db and returns the db record
    {:id :code-hash :enc-key}
     enc-key is decrypted"
  [user token-key]
  (let [token-record (get-reset-token user token-key)]
    (when (not-empty token-record)
      (delete-reset-token (:id token-record))
      (assoc
        token-record
        :enc-key
        (keys/decrypt token-key (:enc-key token-record))))))

(defn gen-unique-keys
  "Return {:ks [unique-key...]
           :hash-and-vals [ [hash-of-key user-enc-key-encrypted-with-key]...] }"
  [user]
  (let [enc-key (auth/user-enc-key user)
        ks (repeatedly 3 #(s-keys/gen-readable-key))
        kh-vals (mapv #(vector (s-keys/api-key-hash nil %)
                               (s-keys/encrypt % enc-key))
                      ks)]

    {:ks            ks
     :hash-and-vals kh-vals}))

(defn create-tokens-store-val [user ks]
  (keys/encrypt (auth/user-enc-key user) (nippy/freeze ks)))

(defn read-token-store-val [user v]
  (when v
    (nippy/thaw (keys/decrypt (auth/user-enc-key user) v))))

(defn update-db-with-reset-tokens [user hash-and-vals]
  (db/with-transaction (fn [_]
                         (db/delete-user-reset-codes! {:user-id (:id user)})

                         (doseq [[code-hash enc-key] hash-and-vals]
                           (db/insert-user-reset-code! {:user-id   (:id user)
                                                        :code-hash (utils/ensure-bytes code-hash)
                                                        :enc-key   (utils/ensure-bytes enc-key)})))))


(defn gen-reset-db-tokens
  "Return {:ks [unique-key...]
            :hash-and-vals [ [hash-of-key user-enc-key-encrypted-with-key]...] }"
  [user]
  (let [{:keys [hash-and-vals] :as ks-record} (gen-unique-keys user)]

    (update-db-with-reset-tokens user hash-and-vals)

    ks-record))

(defn get-reset-tokens-by-id [user token-id]
  (read-token-store-val user (tokens-core/get-token (:id user) token-id)))

(defn gen-reset-tokens
  "Generate reset tokens and update the db,
   save the reset tokens to the token store ttl 5mins
   return the token store id"
  [request & {:keys [user]}]
  (let [user' (or user (auth/user-logged-in? request))
        {:keys [ks]} (gen-reset-db-tokens user')

        token-id (tokens-core/unique-id)]

    ;;save the keys encrypted with the user key to the token store
    (tokens-core/set-token (:id user) token-id (create-tokens-store-val user' ks) (* 5 60))

    token-id))

(defn found [req message errors & {:keys [query-string]}]
  (assoc (response/found (str (:uri req) "?" (if (:query-string req) (str (:query-string req) "&" query-string) query-string)))
    :flash {:errors  errors
            :message message}))

(defn gen-and-show-tokens [request]
  (found request nil nil :query-string (str "token=" (gen-reset-tokens request))))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; email confirm functions

(defn email-confirm-code-id [user]
  {:pre [(:id user)]}
  (str (:id user) "/emailconfirm"))

(defn gen-user-email-confirm-code
  "
  Create a unique key which is saved to the token cache (expire 4hrs)
  If no confirm is done, the key expires and the user has to hit re-send email again.
  "
  [user]
  (let [id (email-confirm-code-id user)
        key (tokens-core/unique-id)
        token {:ts (System/currentTimeMillis)
               :confirm-code key}]
    (tokens-core/set-token id id token 14400)
    token))

(defn get-email-confirm-tokens-by-id
  "Returns {:confirm-code :ts} the confirm code, and the timestamp when the token was sent"
  [user]
  (let [id (email-confirm-code-id user)
        ;;
        v (tokens-core/get-token id id)]
    v))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; route functions

(defn view [{:keys [flash] :as request}]
  (let [user (auth/user-logged-in? request)

        token (get-in request [:params :token])

        ks (when token (let [token-val (tokens-core/get-token (auth/session-id request) token)]
                         (tokens-core/del-token token)
                         (map #(utils/ensure-str %)
                              (read-token-store-val user token-val))))]

    (layout/render*
      request
      "dashboard/settings/resettokens/main.html"
      (merge
        {:user            user
         :user-name       (:user-name user)
         :reset-tokens-active "active"
         :keys            ks}
        (if (and token (not ks))
          {:errors ["Token expired, please click on Generate Reset Tokens"]}
          (select-keys flash [:name :message :errors]))))))

(defn view-security [{:keys [flash] :as request}]
  (let [user (auth/user-logged-in? request)]

    (layout/render*
      request
      "settings_security.html"
      (merge
        {:user            user
         :user-name       (:user-name user)
         :security-active "active"}
        (select-keys flash [:name :message :errors])))))

(defn update-account
  "
  Expects a POST action param for create
  "
  [{:keys [params] :as request}]

  (try
    (let [action (:action params)]
      (cond
        (= action "create") (gen-and-show-tokens request)

        :else
        (do
          (error "Action not supported " action)
          (RuntimeException. (str "Action " action " not implemented")))))
    (catch Exception e
      (error e)
      (found request nil [(str "Internal error while updating plans: " e)]))))


