(ns
  ^{:doc "group passwords


  "}
  keyssrv.routes.groups.dbs
  (:require [keyssrv.users.auth :as auth]
            [keyssrv.groups.core :as groups]
            [keyssrv.routes.sse.pass-group-events :as pass-group-events]
            [keyssrv.db.core :as db]
            [taoensso.nippy :as nippy]
            [ring.util.http-response :as response]
            [keyssrv.layout :as layout]
            [keyssrv.users.limits :as user-limits]
            [keyssrv.utils :as utils]
            [keyssrv.routes.groups.secrets :as group-secrets]
            [clojure.tools.logging :refer [error]]
            [keyssrv.groups.cache :as groups-cached]
            [keyssrv.routes.groups.wizz :as group-wizz]
            [keyssrv.routes.index.wizzards :as wizzards]
            [keyssrv.routes.sse.pass-group-events :as pass-group-events])
  (:import (org.apache.commons.lang3 StringUtils)))


(defn -parse-str-input [v]
  (StringUtils/trimToNull (str v)))

(defn found [req message errors]
  (assoc (response/found (str (:uri req) "?" (:query-string req)))
    :flash {:errors  errors
            :message message}))

(defn get-group-dbs-raw [group-id]
  (db/select-group-dbs {:group-id group-id}))


(defn get-group-dbs [group-id & {:keys [show-id show-secret]}]
  (map
    (fn [{:keys [id gid type lbl]}]

      {:id       id
       :gid      gid
       :type     type

       :lbl      lbl
       :val      (if (= id show-id) show-secret "****")
       :disabled (not (= id show-id))})
    (get-group-dbs-raw group-id)))


(defn group-db-exists-by-id? [group-id id]
  {:pre [id]}
  (let [record (db/get-group-db-by-gid {:group-id group-id :gid id})]
    (when (not-empty record)
      record)))

(defn group-db-exists?
  [group-id lbl]
  (let [record (db/get-group-db-by-lbl {:group-id group-id
                                        :lbl      lbl})]

    (when (not-empty record)
      record)))

(defn insert-group-db [group-id lbl type hosted-on val-obj-enc]
  (db/insert-group-db! {:group-id  group-id
                        :lbl       (or lbl "-")
                        :type      (or type "other")
                        :hosted-on (or hosted-on "")
                        :val-enc   (utils/ensure-bytes val-obj-enc)}))


(defn update-group-db [group-id group-login-id lbl type hosted-on val-enc]
  (db/update-group-db! {:group-id  group-id
                        :id        group-login-id
                        :lbl       lbl
                        :type      type
                        :hosted-on hosted-on
                        :val-enc   (utils/ensure-bytes val-enc)}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; route/public functions


(defn build-db-val-record
  "Build the record that represents the secret information for a db record and is stored as a bytearray"
  [host port database dbuser password]
  {:host     host
   :port     port
   :database database
   :dbuser   dbuser
   :password password})

(defn do-update-group-db [request group-id group-db-id lbl type hosted-on host port database dbuser password]
  (let [user (auth/user-logged-in? request)
        group (groups/pass-group-exists-by-id? (:id user) group-id)
        ]

    (if (empty? group)
      (response/found "/pass/groups")                       ;;The group is no longer available, go back to groups
      (let [group-login (when (not-empty group) (group-db-exists-by-id? (:id group) group-db-id))
            [msg error] (cond

                          (not group-login) [nil (str "The db does not exist")]

                          :else
                          (try
                            (update-group-db (:id group)
                                             (:id group-login)
                                             lbl
                                             type
                                             hosted-on
                                             (group-secrets/encode-secret group user
                                                                          (utils/ensure-bytes
                                                                            (nippy/freeze
                                                                              (build-db-val-record host port database dbuser password)))))
                            ["Updated db record" nil]
                            (catch Throwable e
                              (if (utils/already-exist-exception? e)
                                [nil (str "The db record " lbl " already exist")]
                                (throw e)))))

            ]

        (found request msg (when error [error]))))))

(defn create-group-db [request group-id lbl type hosted-on host port database dbuser password]
  {:pre [group-id]}
  (let [user (auth/user-logged-in? request)
        group (groups/pass-group-exists-by-id? (:id user) group-id)

        db-count (groups/total-user-db-count (:id user))]

    (if (empty? group)
      (response/found "/pass/groups")                       ;;The group is no longer available, go back to groups
      (let [[msg error] (cond
                          (or (nil? lbl)
                              (not (group-secrets/validate-lbl lbl))) [nil group-secrets/LBL_NOT_VALID_MSG]

                          (group-db-exists? (:id group-id) lbl) [nil (str "The db record " lbl " already exist")]

                          (not (< db-count (user-limits/max-logins user))) [nil "Total db record item limit reached, please upgrade your current plan"]
                          :else
                          (try
                            (insert-group-db (:id group) lbl type hosted-on (group-secrets/encode-secret group user
                                                                                                         (utils/ensure-bytes
                                                                                                           (nippy/freeze
                                                                                                             (build-db-val-record host port database dbuser password)))))
                            ["DB added" nil]
                            (catch Throwable e
                              (if (utils/already-exist-exception? e)
                                [nil (str "The db record " lbl " already exist")]
                                (throw e)))))

            ]

        (found request msg (when error [error]))))))

(defn remove-group-db [request group-id group-db-id]
  {:pre [group-id group-db-id]}
  (let [user (auth/user-logged-in? request)
        group (groups/pass-group-exists-by-id? (:id user) group-id)]

    (if (empty? group)
      (response/found "/pass/groups")                       ;;The group is no longer available, go back to groups
      (let [db-record (group-db-exists-by-id? (:id group) group-db-id)
            [msg error] (cond
                          (or (nil? group-db-id)) [nil "The db record provided is not valid"]
                          (not db-record) [nil (str "The db record does not exist")]
                          :else
                          (do
                            (db/delete-group-db-by-id! {:group-id (:id group) :id (:id db-record)})
                            [(str "DB record removed") nil]))

            ]

        (found request msg (when error [error]))))))

(defn view [group-id {:keys [flash] :as request} & {:keys [show-id show-lbl show-secret show-type show-hosted-on]}]
  {:keys [group-id]}
  (let [user (auth/user-logged-in? request)
        group (groups/pass-group-exists-by-id? (:id user) group-id)
        dbs (when (not-empty group) (get-group-dbs (:id group) :show-id show-id :show-secret show-secret))

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
        "dashboard/dbs/main.html"
        (merge
          {:pass-groups       (groups-cached/query-pass-groups-enriched-cached (:id user))
           :group             group
           :user              user
           :user-name         (:name user)
           :dbs               dbs
           :show-id           show-id
           :show-lbl          show-lbl
           :show-val          show-secret
           :show-type         show-type
           :show-hosted-on    show-hosted-on
           :view              "dbs"
           :view-id           (:dbs pass-group-events/VIEW-IDS)
           :dbs-active        "active"
           :show-page-help    false
           :show-update-modal (if show-id true false)

           :hint-wizzard      show-wizz
           }
          (select-keys flash [:name :message :errors]))))))

(defn get-group-dbs-by-lbl [group-id lbls]
  (let [lbls' (filter
                group-secrets/validate-lbl
                lbls)]
    (when (seq lbls')
      (db/get-group-db-by-lbls {:group-id group-id :lbls lbls'}))))


(defn do-show-secret [request group-id group-db-id]
  {:pre [group-id group-db-id]}
  (let [user (auth/user-logged-in? request)
        group (groups/pass-group-exists-by-id? (:id user) group-id)
        record (group-db-exists-by-id? (:id group) group-db-id)]

    (if (empty? group)
      (response/found "/pass/groups")                       ;;The group is no longer available, go back to groups
      (let [[secret error] (cond
                             (empty? record) [nil "DB record does not exist"]
                             :else
                             [(nippy/thaw (group-secrets/decrypt-secret user group record)) nil])
            ]

        (if secret
          (view group-id request :show-id group-db-id :show-lbl (:lbl record) :show-type (:type record) :show-hosted-on (:hosted-on record) :show-secret secret)
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
                              (create-group-db request group-id
                                               (-parse-str-input (:lbl params))
                                               (-parse-str-input (:type params))
                                               (-parse-str-input (:hosted-on params))
                                               (-parse-str-input (:host params))
                                               (-parse-str-input (:port params))
                                               (-parse-str-input (:database params))
                                               (-parse-str-input (:dbuser params))
                                               (-parse-str-input (:password params))))
        (= action "remove") (do
                              (pass-group-events/notify-group-db-change group-id)
                              (remove-group-db request group-id (utils/ensure-uuid (:group-db-id params))))
        (= action "show") (do-show-secret request group-id (utils/ensure-uuid (:group-db-id params)))
        (= action "update") (do
                              (pass-group-events/notify-group-db-change group-id)
                              (do-update-group-db request group-id
                                                  (utils/ensure-uuid (:group-db-id params))
                                                  (-parse-str-input (:new-lbl params))
                                                  (-parse-str-input (:new-type params))
                                                  (-parse-str-input (:new-hosted-on params))
                                                  (-parse-str-input (:new-host params))
                                                  (-parse-str-input (:new-port params))
                                                  (-parse-str-input (:new-database params))
                                                  (-parse-str-input (:new-dbuser params))
                                                  (-parse-str-input (:new-password params))))


        :else
        (do
          (prn ">>>>>!!!!!!!!!!!!!!!!!!!!! action => " action)
          (error "Action not supported " action)
          (RuntimeException. (str "Action " action " not implemented")))))
    (catch Exception e
      (error e)
      (found request nil [(str "Internal error while updating groups: " e)]))))
