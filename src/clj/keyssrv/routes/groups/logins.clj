(ns
  ^{:doc "group passwords


  "}
  keyssrv.routes.groups.logins
  (:require [keyssrv.users.auth :as auth]
            [keyssrv.groups.core :as groups]
            [keyssrv.routes.sse.pass-group-events :as pass-group-events]
            [keyssrv.db.core :as db]
            [ring.util.http-response :as response]
            [keyssrv.layout :as layout]
            [keyssrv.users.limits :as user-limits]
            [keyssrv.utils :as utils]
            [keyssrv.routes.groups.secrets :as group-secrets]
            [clojure.tools.logging :refer [error]]
            [keyssrv.groups.cache :as groups-cached]
            [keyssrv.routes.groups.wizz :as group-wizz]
            [keyssrv.routes.index.wizzards :as wizzards]
            [keyssrv.routes.sse.pass-group-events :as pass-group-events]
            [keyssrv.secret.keys :as keys])
  (:import (org.apache.commons.lang3 StringUtils)))


(defn -parse-str-input [v]
  (StringUtils/trimToNull (str v)))

(defn found [req message errors]
  (assoc (response/found (str (:uri req) "?" (:query-string req)))
    :flash {:errors  errors
            :message message}))

(defn get-group-logins-raw [group-id]
  {:pre [(number? group-id)]}
  (db/select-group-logins {:group-id group-id}))

(defn get-group-logins-by-login [group-id logins lbls]
  {:pre [(or (coll? logins) (coll? lbls))]}

  (let [logins-s (set logins)
        lbls-s (set lbls)]

    (filter
      #(or
         (logins-s (:login %))
         (lbls-s (:lbl %)))
      (db/select-group-logins {:group-id group-id}))))

(defn get-group-logins [group-id & {:keys [show-id show-secret]}]
  (map
    (fn [{:keys [id gid login lbl user-name user-name2 user-name-2]}]

      {:id         id
       :gid        gid
       :user-name  user-name
       :user-name2 (or user-name2 user-name-2)
       :login      (if
                     (and
                       (not login)
                       (StringUtils/startsWith (str lbl) "htt")) lbl
                                                                 login)
       :lbl        lbl
       :val        (if (= id show-id) show-secret "****")
       :disabled   (not (= id show-id))})
    (get-group-logins-raw group-id)))

(defn update-secret? [^String new-secret]
  (and
    new-secret
    (not (StringUtils/contains new-secret "*"))))

;
;(defn group-login-exists-by-id? [group-id id]
;  {:pre [id]}
;  (let [record (db/get-group-login-by-id {:group-id group-id :id id})]
;    (when (not-empty record)
;      record)))

(defn group-login-exists-by-gid? [group-id id]
  {:pre [id]}
  (let [record (db/get-group-login-by-gid {:group-id group-id :gid id})]
    (when (not-empty record)
      record)))

(defn group-login-exists?
  [group-id user-name user-name2 lbl]
  (let [record (db/get-group-login-by-user-names {:group-id   group-id
                                                  :user-name  user-name
                                                  :user-name2 (or user-name2 "-")
                                                  :lbl        lbl})]

    (when (not-empty record)
      record)))

(defn insert-group-login [group-id lbl user-name user-name2 login val-enc]
  (db/insert-group-login! {:group-id   group-id
                           :user-name  user-name
                           :user-name2 (or user-name2 "-")
                           :lbl        (or lbl "-")
                           :login      (or login "-")
                           :val-enc    (utils/ensure-bytes val-enc)}))



(defn update-group-login [group-id group-login-id lbl user-name user-name2 login]
  (db/update-group-login! {:group-id   group-id
                           :id         group-login-id
                           :lbl        lbl
                           :user-name  user-name
                           :user-name2 user-name2
                           :login      login}))

(defn update-group-login-with-secret [group-id group-login-id lbl user-name user-name2 login val-enc]
  (db/update-group-login-and-secret!
    {:group-id   group-id
     :id         group-login-id
     :lbl        lbl
     :user-name  user-name
     :user-name2 user-name2
     :login      login
     :val-enc    val-enc}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; route/public functions


(defn do-update-group-login [request group-id group-login-id lbl user-name user-name2 login secret]
  (let [user (auth/user-logged-in? request)
        group (groups/pass-group-exists-by-id? (:id user) group-id)

        group-login (when (not-empty group) (group-login-exists-by-gid? (:id group) group-login-id))
        update-new-secret (update-secret? secret)]

    (if (empty? group)
      (response/found "/pass/groups")                       ;;The group is no longer available, go back to groups
      (let [[msg error] (cond

                          (not group-login) [nil (str "The login does not exist")]

                          update-new-secret (do
                                              (update-group-login-with-secret (:id group)
                                                                              (:id group-login)
                                                                              lbl
                                                                              user-name
                                                                              user-name2
                                                                              login
                                                                              (group-secrets/encode-secret group user (utils/ensure-bytes secret)))
                                              ["Updated login and secret" nil])

                          :else
                          (try
                            (update-group-login (:id group)
                                                (:id group-login)
                                                lbl
                                                user-name
                                                user-name2
                                                login)
                            ["Updated login" nil]
                            (catch Throwable e
                              (if (utils/already-exist-exception? e)
                                [nil (str "The login " lbl " already exist")]
                                (throw e)))))

            ]

        (found request msg (when error [error]))))))

(defn create-group-login [request group-id lbl user-name user-name2 login secret]
  {:pre [group-id]}
  (let [user (auth/user-logged-in? request)
        group (groups/pass-group-exists-by-id? (:id user) group-id)

        login-count (groups/total-user-login-count (:id user))]

    (if (empty? group)
      (response/found "/pass/groups")                       ;;The group is no longer available, go back to groups
      (let [[msg error] (cond
                          (or (nil? user-name)
                              (nil? secret)) [nil "At least a user name and password must be defined"]
                          (or (nil? lbl)
                              (not (group-secrets/validate-lbl lbl))) [nil group-secrets/LBL_NOT_VALID_MSG]

                          (group-login-exists? (:id group) user-name user-name2 lbl) [nil (str "The login " lbl " already exist")]

                          (not (< login-count (user-limits/max-logins user))) [nil "Total login item limit reached, please upgrade your current plan"]
                          :else
                          (try
                            (insert-group-login (:id group) lbl user-name user-name2 login (group-secrets/encode-secret group user (utils/ensure-bytes secret)))
                            ["Login created" nil]
                            (catch Throwable e
                              (prn "Got message " {:e        e
                                                   :contains (StringUtils/contains (str e) "already exists")})
                              (if (utils/already-exist-exception? e)
                                [nil (str "The login " lbl " already exist")]
                                (throw e)))))

            ]

        (found request msg (when error [error]))))))

(defn remove-group-login [request group-id group-login-id]
  {:pre [group-id group-login-id]}
  (let [user (auth/user-logged-in? request)
        group (groups/pass-group-exists-by-id? (:id user) group-id)
        group-login (group-login-exists-by-gid? (:id group) group-login-id)]

    (if (empty? group)
      (response/found "/pass/groups")                       ;;The group is no longer available, go back to groups
      (let [[msg error] (cond
                          (nil? group-login-id) [nil "The login provided is not valid"]
                          (not group-login) [nil (str "The login does not exist")]
                          :else
                          (do
                            (db/delete-group-login-by-id! {:group-id (:id group) :id (:id group-login)})
                            [(str "Login removed") nil]))

            ]

        (found request msg (when error [error]))))))

(defn view [group-id {:keys [flash] :as request} & {:keys [show-id show-secret]}]
  {:keys [group-id]}
  (let [user (auth/user-logged-in? request)
        group (groups/pass-group-exists-by-id? (:id user) group-id)
        logins (when (not-empty group) (get-group-logins (:id group) :show-id show-id :show-secret show-secret))

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
        "dashboard/logins/main.html"
        (merge
          {:pass-groups    (groups-cached/query-pass-groups-enriched-cached (:id user))
           :group          group
           :user           user
           :user-name      (:name user)
           :logins         logins
           :view           "logins"
           :view-id        (:logins pass-group-events/VIEW-IDS)
           :logins-active  "active"
           :show-page-help false

           :hint-wizzard   show-wizz
           }
          (select-keys flash [:name :message :errors]))))))


(defn do-show-secret [request group-id group-login-id]
  {:pre [group-id group-login-id]}
  (let [user (auth/user-logged-in? request)
        group (groups/pass-group-exists-by-id? (:id user) group-id)
        record (group-login-exists-by-gid? (:id group) group-login-id)]


    ;;; @TODO remove this
    ;(let [enc-key (:enc-key user)
    ;      user-group-master-key-enc (:user-group-master-key-enc group)]
    ;
    ;  (keys/decrypt
    ;    enc-key
    ;    user-group-master-key-enc))

    (if (empty? group)
      (response/found "/pass/groups")                       ;;The group is no longer available, go back to groups
      (let [[secret error] (cond
                             (empty? record) [nil "Login does not exist"]
                             :else
                             [(group-secrets/decrypt-secret user group record) nil])
            ]

        (if secret
          (view group-id request :show-id (:id record) :show-secret (utils/ensure-str secret))
          (found request nil (when error [error])))))))

(defn add-or-remove
  "
  Expects a POST action param for create/delete/update
  create would be /pass/groups?view=secrets POST params: action=create,lbl=<label>,secret=secret
  "
  [group-id {:keys [params] :as request}]
  {:pre [group-id]}

  (try
    (let [action (:action params)]
      (cond
        (= action "create") (do
                              (pass-group-events/notify-group-login-change group-id)
                              (create-group-login request group-id
                                                  (-parse-str-input (:lbl params))
                                                  (-parse-str-input (:user-name params))
                                                  (-parse-str-input (:user-name2 params))
                                                  (-parse-str-input (:login params))
                                                  (-parse-str-input (:secret params))))
        (= action "remove") (do
                              (pass-group-events/notify-group-login-change group-id)
                              (remove-group-login request group-id (utils/ensure-uuid (:group-login-id params))))
        (= action "show") (do-show-secret request group-id (utils/ensure-uuid (:group-login-id params)))
        (= action "update") (do
                              (pass-group-events/notify-group-login-change group-id)
                              (do-update-group-login request group-id
                                                     (utils/ensure-uuid (:group-login-id params))
                                                     (-parse-str-input (:new-lbl params))
                                                     (-parse-str-input (:new-user-name params))
                                                     (-parse-str-input (:new-user-name2 params))
                                                     (-parse-str-input (:new-login params))
                                                     (-parse-str-input (:new-secret params))))


        :else
        (do
          (prn ">>>>>!!!!!!!!!!!!!!!!!!!!! action => " action)
          (error "Action not supported " action)
          (RuntimeException. (str "Action " action " not implemented")))))
    (catch Exception e
      (error e)
      (found request nil [(str "Internal error while updating groups: " e)]))))
