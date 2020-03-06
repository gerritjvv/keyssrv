(ns
  ^{:doc "Group user route public functions"}
  keyssrv.routes.groups.users
  (:require [keyssrv.layout :as layout]
            [keyssrv.users.auth :as auth]
            [keyssrv.users.registration :as reg]
            [keyssrv.db.core :as db]
            [keyssrv.groups.cache :as groups-cached]
            [keyssrv.notification.notify :as notify]
            [ring.util.http-response :as response]
            [keyssrv.secret.keys :as keys]
            [keyssrv.groups.core :as groups]
            [keyssrv.routes.sse.pass-group-events :as pass-group-events]
            [keyssrv.users.limits :as user-limits]
            [clojure.tools.logging :refer [error]]
            [keyssrv.utils :as utils]
            [keyssrv.routes.index.wizzards :as wizzards]
            [keyssrv.routes.groups.wizz :as group-wizz])
  (:import (org.apache.commons.lang3 StringUtils)
           (java.util UUID)))


(defn delete-user-from-group [user-id group-id]
  {:pre [user-id group-id]}
  (db/delete-user-from-group! {:user-id user-id :group-id group-id}))

(defn select-users-for-group [group-id]
  (db/select-password-group-shared-users {:group-id group-id}))



(defn insert-share-request-relation [{:keys [owner user-id group-id user-group-master-key-enc admin]}]
  (db/create-password-group-user! {:owner                     (:id owner)
                                   :user-id                   user-id
                                   :group-id                  group-id
                                   :user-group-master-key-enc user-group-master-key-enc
                                   :confirmed                 false ;;a share request always starts with confirm=false
                                   :is-admin                  (or admin false)}))

(defn found [req message errors]
  (assoc (response/found (str (:uri req) "?" (:query-string req)))
    :flash {:errors  errors
            :message message}))


(defn do-confirm-user-to-group [user {:keys [group-id user-group-master-key-enc]}]
  {:pre [(:enc-key user) group-id user-group-master-key-enc]}
  ;;db/confirm-user-group-share user-group-master-key-enc, :user-id, :group-id

  (let [system-key (keys/system-key)
        decrypted-group-key (keys/decrypt system-key user-group-master-key-enc)

        user-encrypted-group-key (keys/encrypt (:enc-key user) decrypted-group-key)
        ]
    (db/confirm-user-group-share!
      {:group-id                  group-id
       :user-id                   (:id user)
       :user-group-master-key-enc user-encrypted-group-key})))

(defn do-share-request
  "
  Add a group-rel for group-id<->user-id, with confirmed=false and user-group-master-key-enc encrypted
   with the system key.
  owner => {:enc-key :id}"
  [owner {:keys [group-id user-group-master-key-enc]} {:keys [user-id admin]}]
  {:pre [owner group-id user-group-master-key-enc user-id]}
  (let [decrypted-group-key (keys/decrypt (:enc-key owner) user-group-master-key-enc)
        system-key (keys/system-key)
        system-encrypted-group-key (keys/encrypt system-key decrypted-group-key)
        record {:owner                     owner
                :user-id                   user-id
                :admin                     (or admin false)
                :group-id                  group-id
                :user-group-master-key-enc system-encrypted-group-key
                :confirmed                 false}]

    (insert-share-request-relation
      record)
    record))

(defn empty-safe [v]
  (when (not-empty v)
    v))

(defn check-user-exists
  "Checks if user-name exists and calls (f user-db-record)
   otherwise returns a found error"
  [req {:keys [user-name user-id]} f]
  (let [user (if user-name
               (or
                 (empty-safe (reg/get-user-by-email user-name))
                 (empty-safe (reg/get-user-by-user-name user-name)))
               (do
                 (when-not (instance? UUID user-id)
                   (throw (RuntimeException. (str "UserId must be a valid uuid but got "(type user-id)))))
                 (reg/get-user-by-user-gid user-id)))]

    (if (empty? user)
      (found req nil (str "User " user-name " does not exist"))
      (f user))))

(defn trim-txt [v]
  (StringUtils/trim (str v)))

(defn parse-check-box [v]
  (let [v' (StringUtils/lowerCase (str v))]
    (if
      (or (= v' "on")
          (= v' "true")) true
                         false)))

(defn confirm-user-to-group [req group-id]
  (let [user (auth/user-logged-in? req)
        group (groups/pass-group-exists-by-id? (:id user) group-id)
        group-rel (groups/group-rel (:id user) (:id group))]

    (groups-cached/invalidate (:id user))
    (cond
      (or (empty? user) (empty? group-rel)) (found req nil '("This group is not available anymore"))

      :else
      (do
        (do-confirm-user-to-group user group-rel)
        (response/found "/pass/groups")))))

(defn remove-user-from-group [req group-id {:keys [user-id]}]
  (check-user-exists req {:user-id user-id} (fn [user]
                                              (let [owner (auth/user-logged-in? req)
                                                    group (groups/pass-group-exists-by-id? (:id user) group-id)
                                                    ;;get the logged in user's relation with the group to get the enc key
                                                    group-rel (groups/group-rel (:id owner) (:id group))
                                                    group-rel2 (groups/group-rel (:id user) (:id group))]

                                                (groups-cached/invalidate (:id user))

                                                (cond

                                                  ;;allow removing one self from a group
                                                  (and (not-empty group-rel)
                                                       (= (:user-id group-rel) (:id user)))

                                                  (do (delete-user-from-group (:id user) (:id group))
                                                      (assoc (response/found "/pass/groups")
                                                        :flash {:message (str (:user-name user) " removed from group")}))

                                                  ;;check only admins can remove other users
                                                  (or (empty? group-rel)
                                                      (not (:is-admin group-rel))) (found req nil '("You do not have permissions to remove users from this group"))

                                                  ;;owners cannot be removed
                                                  (= (:owner group-rel2) (:id user)) (found req nil '("Owners cannot be removed"))


                                                  :else
                                                  (do

                                                    (delete-user-from-group (:id user) (:id group))
                                                    (found req (str "User " (:user-name user) " removed from group") nil)))))))


(defn send-share-email
  "The owner shares group with the user"
  [owner user group]
  (notify/send-group-share-email owner user group))

(defn add-user-to-group [req group-id {:keys [user-name admin]}]
  (check-user-exists req {:user-name user-name} (fn [user]
                                                  (let [user-id (:id user)
                                                        owner (auth/user-logged-in? req)
                                                        total-user-count (groups/total-user-count (:id owner))
                                                        max-users (user-limits/max-users owner)
                                                        group (groups/pass-group-exists-by-id? (:id owner) group-id)

                                                        ;;get the logged in user's relation with the group to get the enc key
                                                        group-rel (groups/group-rel (:id owner) (:id group))
                                                        group-rel2 (groups/group-rel user-id (:id group))]

                                                    (groups-cached/invalidate user-id)
                                                    (groups-cached/invalidate (:id owner))

                                                    (cond

                                                      (or (empty? group-rel)
                                                          (not (:is-admin group-rel))) (found req nil '("You do not have permissions to assign users to this group"))

                                                      (seq group-rel2) (found req nil '("User is already in group"))

                                                      (not (< total-user-count max-users)) (found req nil '("Total user limit reached, please upgrade your current plan"))

                                                      :else
                                                      (do
                                                        (do-share-request owner group-rel {:user-id user-id :admin admin})
                                                        (send-share-email owner user group)
                                                        (found req "User added to group" nil)))))))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; public functions

(defn view [group-id {:keys [flash] :as request}]
  (let [user (auth/user-logged-in? request)
        group (groups/pass-group-exists-by-id? (:id user) group-id)
        wizz-i (auth/user-wizz-i user)
        step-i (auth/user-step-i user)

        [wizz-k step-k wizz-def] (wizzards/get-wizzard wizz-i step-i)

        [request' show-wizz] (group-wizz/apply-wizz-group-items-view-updates request user
                                                                             wizz-i
                                                                             step-i
                                                                             wizz-k
                                                                             step-k
                                                                             wizz-def)]

    (if (empty? group)
      (response/found "/pass/groups")                       ;;The group is no longer available, go back to groups
      (layout/render*
        request'
        "dashboard/users/main.html"
        (merge
          {
           :pass-groups    (groups-cached/query-pass-groups-enriched-cached (:id user))
           :group          group
           :user           user
           :user-name      (:user-name user)
           :users          (select-users-for-group (:id group))
           :view           "users"
           :view-id        (:users pass-group-events/VIEW-IDS)
           :users-active   "active"
           :show-page-help false
           :hint-wizzard   show-wizz
           }
          (select-keys flash [:name :message :errors]))))))

(defn add-or-remove-confirm-user [group-id {:keys [params] :as req}]
  {:pre [group-id]}
  (try
    (cond
      (:user-id params) (do
                          (pass-group-events/notify-group-user-change group-id)
                          (remove-user-from-group req group-id  (update params :user-id utils/ensure-uuid)))
      (:confirmed params) (confirm-user-to-group req group-id)
      :else (do
              (pass-group-events/notify-group-user-change group-id)
              (add-user-to-group req group-id (-> params
                                                  (update :admin parse-check-box)
                                                  (update :user-name trim-txt)))))
    (catch Exception e
      (error e)
      (found req nil [(str "Internal error while updating groups: " e)]))))
