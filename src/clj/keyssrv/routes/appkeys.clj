(ns
  ^{:doc
    "
    View cli application keys
    "}
  keyssrv.routes.appkeys
  (:require
    [keyssrv.db.core :as db]
    [keyssrv.users.auth :as auth]
    [keyssrv.layout :as layout]
    [keyssrv.routes.route-utils :as route-utils]
    [keyssrv.secret.keys :as keys]
    [keyssrv.secret.appkeys :as akeys]
    [keyssrv.utils :as utils]
    [clj-time.format :as f]
    [clj-time.core :as t]))

(defonce DATE-FORMATTER (f/formatter "yyyy-MM-dd HH:mm:ss"))

(declare view-app-keys)

(defn get-user-app-keys [user]
  {:pre [(:id user)]}
  (db/get-user-app-keys {:user-id (:id user)}))

(defn create-user-app-key
  "
  Creates a new key and inserts the key-id, date-expire and key-secret-hash into the db
  Returns {:keys-id :keys-secret :date-expire}

  Encrypts the user's decrypted enc-key and saves it in the app_keys :enc-key column
  "
  [user date-expire']
  {:pre [(bytes? (:enc-key user))]}
  (let [

        key-id (akeys/gen-key-id)
        key-secret (akeys/gen-key-secret)
        key-secret-hash (keys/api-key-hash nil key-secret)
        date-expire (or date-expire' (t/from-now (t/years 1)))

        ;; we encrypt the user's decrypted encryption key with the app key's secret
        ;; this allows the secret key to be used to decrypt all the user's data
        enc-key-enc (keys/encrypt (akeys/key-secret-as-bytes key-secret) (:enc-key user))
        ]


    (db/create-user-app-key! {:user-id         (:id user)
                              :key-id          key-id
                              :key-secret-hash (utils/ensure-bytes key-secret-hash)
                              :enc-key         enc-key-enc
                              :date-expire     date-expire})

    {:key-id      key-id
     :key-secret  key-secret
     :enc-key-enc enc-key-enc
     :date-expire date-expire}))

(defn create-app-key [{:keys [params] :as request}]
  (let [user (auth/user-logged-in? request)
        date-expire (f/parse (utils/ensure-str (:dateexpire params)))]

    (cond
      (not date-expire) (route-utils/found-same-page request nil "Invalid expire date time format")
      :else (let [record (create-user-app-key user date-expire)]
              (view-app-keys request
                             :show-key-id (:key-id record)
                             :show-key-secret (:key-secret record)
                             :show-key-date-expire (f/unparse DATE-FORMATTER date-expire))))))

(defn delete-app-key [request]
  (let [params (:params request)
        user (auth/user-logged-in? request)]

    (db/delete-user-app-key! {:user-id (:id user)
                              :id      (utils/ensure-int (:id params))})

    (route-utils/found-same-page request "App key deleted" nil)))

(defn create-or-remove [{:keys [params] :as request}]
  (let [action (:action params)]

    (cond
      (= action "create") (create-app-key request)
      (= action "remove") (delete-app-key request)
      :else
      (throw (RuntimeException. (str "Action not supported " action))))))


(defn view-app-keys [request & {:keys [show-key-id show-key-secret show-key-date-expire]}]

  (let [user (auth/user-logged-in? request)

        app-keys (get-user-app-keys user)
        ]

    (layout/render*
      request
      "dashboard/appkeys/main.html"
      (merge
        {:user                     user
         :app-keys                 (map #(assoc %
                                           :date-expire-str
                                           (f/unparse DATE-FORMATTER (:date-expire %))
                                           :date-created-str
                                           (f/unparse DATE-FORMATTER (:date-created %)))

                                        app-keys)
         :show-key-id              show-key-id
         :show-key-secret          show-key-secret
         :show-key-date-expire     show-key-date-expire
         :app-keys-active          "active"
         :default-expire-date-time (f/unparse DATE-FORMATTER (t/from-now (t/years 1)))
         :show-page-help           false
         }
        (select-keys (:flash request) [:name :message :errors])))))