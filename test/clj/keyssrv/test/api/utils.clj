(ns keyssrv.test.api.utils
  (:require [clojure.test :refer :all]
            [keyssrv.test.utils :as test-utils]
            [keyssrv.routes.groups.secrets :as group-secrets]
            [keyssrv.routes.groups.logins :as group-logins]
            [keyssrv.routes.groups.snippets :as group-snippets]
            [keyssrv.routes.groups.certs :as group-certs]
            [keyssrv.routes.groups.envs :as group-envs]
            [keyssrv.routes.groups.dbs :as group-dbs])
  (:import (java.util Base64)))


(defn session-with-params [user group-id m]
  (assoc
    (test-utils/session user group-id)
    :params m))

(defn auth-header [{:keys [user-name password]}]
  {:pre [user-name password]}

  (str "Basic:" (.encodeToString (Base64/getEncoder) (.getBytes (str user-name ":" password) "UTF-8"))))

(defn auth-header-from-key [{:keys [key-id key-secret]}]
  {:pre [key-id key-secret]}
  (str key-id ":" key-secret))

(defn with-data [f]
  (let [owner (test-utils/create-user :plan-type :pro)

        group (test-utils/create-group owner)

        app-key (test-utils/create-user-app-key owner)
        auth-header (auth-header-from-key app-key)
        ]

    (f {:owner owner :group group :app-key app-key :auth-header auth-header})))


(defn with-secret-data [f]
  (with-data (fn [{:keys [owner group app-key auth-header]}]
               (let [lbl (test-utils/unique-str)
                     secret (test-utils/unique-str)
                     group-id (:id group)
                     _ (test-utils/test-no-ui-errors
                         (group-secrets/add-or-remove (:gid group) (session-with-params owner group-id
                                                                                    {:action "create"
                                                                                     :lbl    lbl
                                                                                     :secret secret})))]
                 (f {:owner owner :group group :app-key app-key :auth-header auth-header
                     :lbl   lbl :secret secret})))))


(defn create-login [owner group]
  (let [login (test-utils/unique-str)
        lbl (test-utils/unique-str)
        user-name (test-utils/unique-str)
        user-name2 (test-utils/unique-str)
        group-id (:id group)
        secret (test-utils/unique-str)

        _ (test-utils/test-no-ui-errors
            (group-logins/add-or-remove (:gid group) (session-with-params owner group-id
                                                                      {:action     "create"
                                                                       :lbl        lbl
                                                                       :login      login
                                                                       :user-name  user-name
                                                                       :user-name2 user-name2
                                                                       :secret     secret})))]
    {:login login :lbl lbl :user-name user-name :user-name2 user-name2 :secret secret}))

(defn create-snippet [owner group]
  (let [title (test-utils/unique-str)
        val (test-utils/unique-str)
        group-id (:id group)

        _ (test-utils/test-no-ui-errors
            (group-snippets/add-or-remove (:gid group) (session-with-params owner group-id
                                                                        {:action "create"
                                                                         :title  title
                                                                         :val    val})))]

    {:title title :val val}))

(defn with-cert-data [f]
  (with-data (fn [{:keys [owner group app-key auth-header]}]
               (let [lbl (test-utils/unique-str)
                     pub (test-utils/unique-str)
                     priv (test-utils/unique-str)

                     group-id (:id group)
                     _ (test-utils/test-no-ui-errors
                         (group-certs/add-or-remove (:gid group) (session-with-params owner group-id
                                                                                      {:action   "create"
                                                                                       :lbl      lbl
                                                                                       :pub-key  pub
                                                                                       :priv-key priv})))]
                 (f {:owner owner :group group :app-key app-key :auth-header auth-header
                     :lbl   lbl :pub-key pub :priv-key priv})))))

(defn
  create-env
  "if env-data is not specified a unique string is created"
  [owner group & {:keys [env-data]}]
  (let [lbl (test-utils/unique-str)
        val (or env-data (test-utils/unique-str))
        group-id (:id group)

        _ (test-utils/test-no-ui-errors
            (group-envs/add-or-remove (:gid group) (session-with-params owner group-id
                                                                        {:action "create"
                                                                         :lbl    lbl
                                                                         :val    val})))]
    {:lbl lbl :val val}))

;{
; "lbl": "string",
;      "type": "string",
; "hosted-on": "string",
; "val": {
;         "host": "string",
;               "port": "string",
;         "database": "string",
;         "dbuser": "string",
;         "password": "string"
;         }
; }
;]

;(-parse-str-input (:lbl params))
;(-parse-str-input (:type params))
;(-parse-str-input (:hosted-on params))
;(-parse-str-input (:host params))
;(-parse-str-input (:port params))
;(-parse-str-input (:database params))
;(-parse-str-input (:dbuser params))
;(-parse-str-input (:password params))

(defn
  create-db
  ""
  [owner group & {:keys [db-data]}]
  {:pre [(or (nil? db-data) (map? db-data))]}
  (let [lbl (test-utils/unique-str)

        group-id (:id group)
        db-data' (or
                   db-data
                   {:type      "Postgres"
                    :hosted-on "aws"
                    :host      "localhost"
                    :database  (test-utils/unique-str)
                    :dbuser    (test-utils/unique-str)
                    :password  (test-utils/unique-str)})

        _ (test-utils/test-no-ui-errors
            (group-dbs/add-or-remove (:gid group) (session-with-params owner group-id
                                                                       (merge
                                                                         {:action "create"
                                                                          :lbl    lbl
                                                                          }
                                                                         db-data'))))]

    (merge
      {:lbl lbl}
      db-data')))

(defn with-login-data [f]
  (with-data (fn [{:keys [owner group app-key auth-header]}]
               (let [logins (repeatedly 3 (partial create-login owner group))]
                 (f {:owner  owner :group group :app-key app-key :auth-header auth-header
                     :logins (into [] logins)})))))

(defn with-snippet-data [f]
  (with-data (fn [{:keys [owner group app-key auth-header]}]
               (let [snippets (repeatedly 3 (partial create-snippet owner group))]
                 (f {:owner    owner :group group :app-key app-key :auth-header auth-header
                     :snippets (into [] snippets)})))))

(defn with-env-data
  ([f]
   (with-env-data nil f))
  ([env-data f]
   {:pre [(or (nil? env-data) (string? env-data)) (fn? f)]}
   (with-data (fn [{:keys [owner group app-key auth-header]}]
                (let [envs (repeatedly 3 #(create-env owner group :env-data env-data))]
                  (f {:owner owner :group group :app-key app-key :auth-header auth-header
                      :envs  (into [] envs)}))))))



;; db-data : [{:type "Postgres"
;                    :hosted-on "aws"
;                    :host "localhost"
;                    :database (test-utils/unique-str)
;                    :dbuser (test-utils/unique-str)
;                    :password (test-utils/unique-str)}]
;;; call f as (f {:owner string :group string :app-key string :auth-header string :dbs [db-data]})
(defn with-db-data
  ([f]
   (with-db-data nil f))
  ([db-data f]
   {:pre [(or (nil? db-data) (and
                               (vector? db-data)
                               (map? (first db-data)))) (fn? f)]}
   (with-data (fn [{:keys [owner group app-key auth-header]}]
                (let [dbs (if db-data
                            (mapv #(create-db owner group :db-data %) db-data)
                            (repeatedly 3 #(create-db owner group)))]
                  (f {:owner owner :group group :app-key app-key :auth-header auth-header
                      :dbs   (into [] dbs)}))))))
