(ns
  ^{:doc "Password groups"}
  keyssrv.routes.pass-groups
  (:require [keyssrv.layout :as layout]
            [keyssrv.users.auth :as auth]
            [keyssrv.routes.index.wizzards :as wizz]
            [ring.util.http-response :as response]
            [keyssrv.groups.core :as groups]
            [keyssrv.groups.cache :as groups-cached]
            [keyssrv.routes.groups.users :as group-users]
            [keyssrv.routes.groups.secrets :as group-secrets]
            [keyssrv.routes.groups.logins :as group-logins]
            [keyssrv.routes.groups.dbs :as group-dbs]
            [keyssrv.routes.groups.snippets :as group-snippets]
            [keyssrv.routes.groups.certs :as group-certs]
            [keyssrv.routes.groups.envs :as group-envs]
            [keyssrv.users.limits :as user-limits]

            [keyssrv.sessions :as sessions]

            [keyssrv.routes.index.wizz-util :as wizz-util]
            [clojure.tools.logging :refer [info error]]
            [keyssrv.utils :as utils]
            [keyssrv.routes.route-utils :as route-utils]
            [keyssrv.compress :as c]
            [taoensso.nippy :as nippy]
            [keyssrv.routes.user-events :as user-events])
  (:import (org.apache.commons.lang3 StringUtils)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;; private functions


(defn check-apply-wizzards [request])
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;; public functions


;;;;;;;;;;;;;;; Group Top Level

(def hint-show-explore-i (wizz/get-step-i ::wizz/init-hints-show-explore))
(def hint-init-end-i (wizz/get-step-i ::init-hints-end))


(defn apply-wizz-group-view-updates
  "Wizzard logic for group view

  return [next-step show-wizz?]
   "
  [request user cached-group-names wizz-i step-i wizz-k step-k wizz-def]

  (if (= wizz-k ::wizz/init-hints)

    (let [
          next-step-i (wizz/get-step-i (wizz/step-forward wizz-def step-k))
          update-f (partial wizz-util/update-user-wizz request user)
          ]
      ;
      ;(prn "@@@@@@@@@@@@@@ apply-wizz-group-view-updates " {:next-step-i next-step-i
      ;                                                      :step-k      step-k
      ;                                                      :step-i      step-i
      ;                                                      })
      (cond
        ;;if before show-exlore, advance by one, don't advance past explore
        (< step-i hint-show-explore-i) [(update-f wizz-i next-step-i) true]

        (zero? (count cached-group-names)) [request true]
        ;; if at end, end wizzard
        (= step-i hint-init-end-i) [(update-f 0 0) false]
        :else [request false]))
    [request false]))

(defn view [{:keys [flash] :as request}]
  (let [user (auth/user-logged-in? request)
        wizz-i (auth/user-wizz-i user)
        step-i (auth/user-step-i user)
        [wizz-k step-k wizz-def] (wizz/get-wizzard wizz-i step-i)]

    (if (= wizz-k ::wizz/setup)
      (route-utils/found "/home")
      (let [
            cached-group-names (groups-cached/query-pass-groups-enriched-cached (:id user))
            [request' show-wizz] (apply-wizz-group-view-updates request user cached-group-names wizz-i step-i wizz-k step-k wizz-def)
            ]
        (assoc
          (layout/render*
            request'
            "dashboard/main.html"
            (merge
              {:pass-groups    cached-group-names
               :user           user
               :user-name      (:user-name user)
               :vaults-active  "active"

               :show-page-help false
               :hint-wizzard   show-wizz

               }
              (select-keys flash [:name :message :errors])))
          :session
          (:session request'))))))


(defn create [{:keys [params] :as request}]
  (let [group-name (:name params)
        admin true
        user (auth/user-logged-in? request)

        total-group-count (groups/total-group-count (:id user))

        [error message] (cond
                          (StringUtils/isBlank (str group-name)) ["Please provide a vault name" nil]

                          (not (< total-group-count (user-limits/max-vaults user))) ["Total vault limit reached, please upgrade your current plan"]

                          (groups/pass-group-exists? (:id user) group-name) ["The vault already exists" nil]

                          :else
                          (try

                            (groups/create-pass-group user group-name admin)
                            [nil (str "Safe " group-name " created")]
                            (catch Exception e
                              (do
                                (error e)
                                ["Internal error, please try again" nil]))))]


    ;;invalidate cache
    (groups-cached/invalidate (:id user))

    (assoc
      (response/found "/pass/groups")
      :flash {:errors  error
              :message message})))

(defn delete [{:keys [params] :as request}]

  (let [group-id (utils/ensure-uuid (:group-id params))
        user (auth/user-logged-in? request)
        group (groups/pass-group-exists-by-id? (:id user) group-id)
        is-owner (= (:owner group) (:id user))

        [errors message] (cond
                           (or (not group-id)
                               (not group)) ["invalid request" nil]

                           ;; if user is not owner of the group, remove the user from group
                           (and
                             (not is-owner)
                             (= (:user-id group) (:id user))) (do (group-users/delete-user-from-group (:id user) (:id group))
                                                                  [nil (str "Un-subscribed from Safe " (:name group))])

                           (not (groups/group-is-empty? group)) [(str "Safe " (:name group) " is no empty, please remove all items from it first") nil]
                           :else (if is-owner
                                   (do
                                     (groups/delete-group user (:id group))
                                     [nil (str "Deleted Safe " (:name group))])
                                   ["Only owners can delete Safes"]))]


    ;;invalidate cache
    (groups-cached/invalidate (:id user))

    (assoc
      (response/found "/pass/groups")
      :flash {:errors  errors
              :message message})))

(defn ajax-update-hints-end
  "Called in app.js to disable the hints"
  [req]
  (let [user (auth/user-logged-in? req)
        session' (:session (wizz-util/update-user-wizz req user 0 0))]
    (sessions/update-session req session')

    (layout/ajax-response {})))

(defn create-or-delete
  "Handles delete and create via a POST request
   its considered a delete  if the group-id parameter is present"
  [{:keys [params] :as req}]


  (let [action (:action params)]

    (cond
      (= action "hints-end") (ajax-update-hints-end req)
      (:group-id params) (delete req)
      :else
      (create req))))

;;;;;;;;;;;;;;; Group Sub Items: Users, Passwords, Keys ...

(defn view-group-items
  "View group sub items users/secrets/keys etc
  example for user view /pass/groups?view=user"
  [group-id view request]
  (let [groupUuid (utils/ensure-uuid group-id)]
    (cond
      (= view "users") (group-users/view groupUuid request)
      (= view "secrets") (group-secrets/view groupUuid request)
      (= view "logins") (group-logins/view groupUuid request)
      (= view "dbs") (group-dbs/view groupUuid request)
      (= view "snippets") (group-snippets/view groupUuid request)
      (= view "certs") (group-certs/view groupUuid request)
      (= view "envs") (group-envs/view groupUuid request)


      :else
      (do
        (error "View not found: " view)
        (group-logins/view groupUuid request)))))

(defn create-or-delete-group-items
  "CRUD for users/secrets/keys etc that are sub group items
   example for user create /pass/groups?view=users POST ..."
  [group-id view request]
  (let [groupUuid (utils/ensure-uuid group-id)]
    (prn "create-or-delete-group-items --> group-id: " group-id)
    (cond
      (= view "users") (group-users/add-or-remove-confirm-user groupUuid request)
      (= view "secrets") (group-secrets/add-or-remove groupUuid request)
      (= view "logins") (group-logins/add-or-remove groupUuid request)
      (= view "dbs") (group-dbs/add-or-remove groupUuid request)
      (= view "snippets") (group-snippets/add-or-remove groupUuid request)
      (= view "certs") (group-certs/add-or-remove groupUuid request)
      (= view "envs") (group-envs/add-or-remove groupUuid request)


      :else
      (throw (RuntimeException. (str "View: " view " not recognised"))))))

(defn view-select-pass-group
  "Called from the passgroups_base select, when a user uses the select/search to select a new group"
  [{:keys [params]}]
  (let [group-id (utils/ensure-uuid (:group-id params))
        view (utils/ensure-str (:view params))]

    ;;redirect to select the correct pass/group (vault) as if a link was used
    (response/found (str "/pass/groups/" group-id "?view=" view))))

(defn api-get-secrets [user group-name lbls]
  {:pre [(seq user) (string? group-name) (coll? lbls)]}
  (when (nil? user)
    (throw (RuntimeException. (str "User cannot be nil here"))))

  (let [group (groups/pass-group-exists? (:id user) group-name)

        secrets (when group (group-secrets/get-group-secrets-by-lbls (:id group) lbls))]


    (when secrets
      (map #(array-map :lbl (:lbl %)
                       :val (utils/ensure-str
                              (group-secrets/decrypt-secret user group %)))

           secrets))))

(defn api-get-logins [user group-name & {:keys [logins lbls]}]
  {:pre [(seq user) (string? group-name) (or (coll? logins)
                                             (coll? lbls))]}
  (when (nil? user)
    (throw (RuntimeException. (str "User cannot be nil here"))))
  (let [group (groups/pass-group-exists? (:id user) group-name)
        login-records (when group (group-logins/get-group-logins-by-login (:id group) logins lbls))]


    (when login-records
      (map #(array-map :login (:login %)
                       :lbl (:lbl %)
                       :user-name (:user-name %)
                       :user-name2 (:user-name-2 %)
                       :secret (utils/ensure-str
                                 (group-secrets/decrypt-secret user group %)))

           login-records))))

(defn api-get-snippets [user group-name lbls]
  {:pre [(seq user) (string? group-name) (coll? lbls)]}
  (when (nil? user)
    (throw (RuntimeException. (str "User cannot be nil here"))))

  (let [group (groups/pass-group-exists? (:id user) group-name)
        snippets (when group (group-snippets/get-group-snippets-by-titles (:id group) lbls))]


    (when snippets
      (map #(array-map :title (:title %)
                       :val (utils/ensure-str
                              (c/decompress
                                (group-secrets/decrypt-secret user group %))))

           snippets))))

(defn api-get-certs [user group-name lbls]
  {:pre [(seq user) (string? group-name) (coll? lbls)]}
  (when (nil? user)
    (throw (RuntimeException. (str "User cannot be nil here"))))

  (let [group (groups/pass-group-exists? (:id user) group-name)
        certs (when group (group-certs/get-group-certs-by-lbls (:id group) lbls))]

    (when certs
      (map #(array-map :lbl (:lbl %)
                       :pub-key (utils/ensure-str
                                  (c/decompress (:pub-key-comp %)))
                       :priv-key (utils/ensure-str
                                   (c/decompress
                                     (group-secrets/decrypt-secret user group %))))

           ;;ensure when assign-to is specified we only return the certs if the calling
           ;;user is assigned to the cert.
           (filter #(or
                      (not (:assigned-to %))
                      (= (:id user) (:assigned-to %)))
                   certs)))))

(defn api-get-envs [user group-name lbls]
  {:pre [(seq user) (string? group-name) (coll? lbls)]}
  (when (nil? user)
    (throw (RuntimeException. (str "User cannot be nil here"))))

  (let [group (groups/pass-group-exists? (:id user) group-name)
        envs (when group (group-envs/get-group-envs-by-lbls (:id group) lbls))]
    
    (when envs
      (map #(array-map :lbl (:lbl %)
                       :val (utils/ensure-str
                              (c/decompress
                                (group-secrets/decrypt-secret user group %))))

           ;;ensure when assign-to is specified we only return the certs if the calling
           ;;user is assigned to the cert.
           envs))))

(defn parse-db-val
  "Ensure that no nils but empty strings"
  [{:keys [host port database dbuser password]}]
  {:host     (str host)
   :port     (str port)
   :database (str database)
   :dbuser   (str dbuser)
   :password (str password)})

(defn api-get-dbs [user group-name lbls]
  {:pre [(seq user) (string? group-name) (coll? lbls)]}
  (when (nil? user)
    (throw (RuntimeException. (str "User cannot be nil here"))))

  (let [group (groups/pass-group-exists? (:id user) group-name)

        secrets (when group (group-dbs/get-group-dbs-by-lbl (:id group) lbls))]


    (when secrets
      (map #(merge (array-map :lbl (:lbl %)
                              :type (:type %)
                              :hosted-on (:hosted-on %))
                   (parse-db-val
                     (nippy/thaw
                       (utils/ensure-bytes
                         (group-secrets/decrypt-secret user group %)))))

           secrets))))