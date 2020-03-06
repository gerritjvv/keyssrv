(ns
  ^{:doc "

  Services layer for groups
   Any helper or secondary function in the groups routes are moved here when they do not rely on any
   display or route logic
   "}
  keyssrv.groups.core
  (:require
    [keyssrv.secret.keys :as keys]
    [camel-snake-kebab.core :as ccore]
    [camel-snake-kebab.extras :refer [transform-keys]]
    [keyssrv.db.core :as db])
  (:import (com.google.common.cache CacheBuilder CacheLoader)
           (java.util UUID)))


(defn create-pass-group
  "return: group record with :id"
  [{:keys [id enc-key]} group-name admin & {:keys [confirmed]}]
  {:pre [id enc-key (string? group-name) (boolean admin)]}

  (let [user-id id
        k (keys/gen-key)
        k-enc (keys/encrypt enc-key k)]
    (transform-keys
      ccore/->kebab-case-keyword
      (db/with-transaction (fn [_]
                             (let [group (db/create-password-group! {:user-id user-id
                                                                     :name    group-name})
                                   group-id (:id group)]

                               (when-not group-id
                                 (throw (RuntimeException. (str "DB did not return any group id"))))

                               (db/create-password-group-user!
                                 {:owner                     user-id
                                  :user-id                   user-id
                                  :group-id                  group-id
                                  :user-group-master-key-enc k-enc
                                  :is-admin                  admin
                                  :confirmed                 (or confirmed true)})
                               group))))))

(defn delete-group
  "user => {:id}
   group-id => number"
  [user group-id]
  {:pre [(:id user) group-id]}
  (db/delete-user-group! {:user-id (:id user) :group-id group-id}))


(defn pass-group-exists?
  "Returns the same as pass-group-exists-by-id?"
  [user-id gname]
  {:pre [user-id gname]}
  (let [record (db/get-user-password-group-by-name {:user-id user-id :name gname})]
    (when (seq record)
      record)))

(defn pass-group-exists-by-id?
  "Return the passgroup that was either created by or shared with the user
  return
  g.id
  g.gid
  g.name
  pgu.owner
  pgu.group_id
  pgu.user_id
  pgu.user_group_master_key_enc
  pgu.is_admin
  pgu.confirmed

  "
  [user-id group-id]
  {:pre [user-id group-id (instance? UUID group-id)]}
  (prn "pass-group-exists-by-id?: " {:group-id group-id :user-id user-id})
  (let [record (db/get-user-password-group-by-id {:user-id user-id :group-id group-id})]
    (when (seq record)
      record)))

(defn group-is-empty?
  "Should return true if no password items in this group
  @TODO implement
  "
  [group-db-record]
  true)


;; get the passgroups that have been shared to or created by this user
(defn query-pass-groups [{:keys [id]}]
  {:pre [id]}
  (db/select-user-password-groups {:user-id id}))


(defn query-pass-groups-enriched [user]
  (let [cache (->
                (CacheBuilder/newBuilder)
                (.maximumSize 1000)
                (.build (proxy
                          [CacheLoader]
                          []
                          (load [user-id]
                            (select-keys (db/get-user-by-id {:id user-id})
                                         [:id :user-name])))))]
    (map
      (fn [group-rel]
        (assoc
          group-rel
          :owner-user (.get cache (:owner group-rel))))
      (query-pass-groups user))))


(defn total-user-count [user-id]
  {:pre [user-id]}
  (:count (db/select-total-share-user-count {:user-id user-id})))

(defn total-group-count [user-id]
  {:pre [user-id]}
  (:count (db/select-total-user-group-count {:user-id user-id})))

(defn total-user-cert-count [user-id]
  {:pre [user-id]}
  (:count (db/select-total-user-certs {:user-id user-id})))

(defn total-user-snippet-count [user-id]
  {:pre [user-id]}
  (:count (db/select-total-user-snippets {:user-id user-id})))

(defn total-user-env-count [user-id]
  {:pre [user-id]}
  (:count (db/select-total-user-envs {:user-id user-id})))


(defn total-user-login-count [user-id]
  {:pre [user-id]}
  (:count (db/select-total-user-logins {:user-id user-id})))

(defn total-user-db-count [user-id]
  {:pre [user-id]}
  (:count (db/select-total-user-dbs {:user-id user-id})))


(defn total-user-secret-count [user-id]
  {:pre [user-id]}
  (:count (db/select-total-user-secrets {:user-id user-id})))

(defn group-rel
  "Returns the user's group relation if any
     {:owner :user-id :group-id :user-group-master-key-enc :is-admin :confirmed}
  "
  [user-id group-id]
  (db/select-password-group-rel-shared-with-user {:user-id user-id :group-id group-id}))
