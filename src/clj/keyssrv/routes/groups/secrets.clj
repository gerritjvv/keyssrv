(ns
  ^{:doc "group passwords


  "}
  keyssrv.routes.groups.secrets
  (:require [keyssrv.users.auth :as auth]
            [keyssrv.groups.core :as groups]
            [keyssrv.routes.sse.pass-group-events :as pass-group-events]
            [keyssrv.db.core :as db]
            [ring.util.http-response :as response]
            [keyssrv.layout :as layout]
            [keyssrv.users.limits :as user-limits]
            [keyssrv.utils :as utils]
            [keyssrv.secret.keys :as keys]
            [clojure.tools.logging :refer [error]]
            [keyssrv.groups.cache :as groups-cached]
            [keyssrv.routes.groups.wizz :as group-wizz]
            [keyssrv.routes.index.wizzards :as wizzards]
            [keyssrv.routes.sse.pass-group-events :as pass-group-events])
  (:import (org.apache.commons.lang3 StringUtils)))

(defonce LBL_NOT_VALID_MSG "The label provided is not valid, max 100 characters and only alphanumeric and special characters '_' '-' '/' are allowed")
(defonce VALID-LBL-REGEX #"^[a-zA-Z0-9_\-/\:\?\&\%\.]+$")

(defn validate-lbl [lbl]
  (and
    (<= (count lbl) 100)
    (re-matches VALID-LBL-REGEX lbl)))

(defn -parse-str-input [v]
  (StringUtils/trimToNull (str v)))

(defn found [req message errors]
  (assoc (response/found (str (:uri req) "?" (:query-string req)))
    :flash {:errors  errors
            :message message}))

(defn get-group-secrets-by-lbls [group-id lbls]
  (let [f-lbls (filter validate-lbl lbls)]
    (when (seq f-lbls)
      (db/get-secrets-by-lbls
        ;;important validate the lbls before they go ito the in query
        {:group-id group-id
         :lbls f-lbls}))))

(defn get-group-secrets-raw [group-id]
  (db/select-secrets {:group-id group-id}))

(defn get-group-secrets [group-id & {:keys [show-lbl show-secret]}]
  (map
    (fn [{:keys [lbl]}]
      {:lbl      lbl
       :val      (if (= lbl show-lbl) show-secret "****")
       :disabled (not (= lbl show-lbl))})
    (get-group-secrets-raw group-id)))


(defn update-lbl? [^String current-lbl ^String new-lbl]
  (and
    current-lbl
    new-lbl
    (validate-lbl new-lbl)
    (not= current-lbl new-lbl)))


(defn update-secret? [^String new-secret]
  (and
    new-secret
    (not (StringUtils/contains new-secret "*"))))


(defn lbl-exists? [group-id lbl]
  (let [record (db/get-secret-by-lbl {:group-id group-id :lbl lbl})]
    (when (not-empty record)
      record)))

(defn insert-secret [group-id lbl val-enc]
  (db/insert-secret! {:group-id group-id :lbl lbl :val-enc (utils/ensure-bytes val-enc)}))

(defn encode-secret [{:keys [user-group-master-key-enc]} {:keys [enc-key]} secret]
  {:pre [user-group-master-key-enc enc-key secret]}
  (when-not (or user-group-master-key-enc enc-key secret)
    (throw (RuntimeException. (str "keys cannot be null"))))

  (keys/encrypt
    (keys/decrypt enc-key user-group-master-key-enc)
    (utils/ensure-bytes secret)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; route/public functions

(defn update-lbl-and-secret [group-id current-lbl new-lbl new-val-enc]
  {:pre [group-id current-lbl new-lbl new-val-enc]}
  (db/update-secret-lbl-and-val! {:group-id group-id
                                  :lbl      current-lbl
                                  :new-lbl  new-lbl
                                  :val-enc  new-val-enc}))

(defn update-lbl [group-id current-lbl new-lbl]
  (db/update-secret-lbl! {:group-id group-id
                          :lbl      current-lbl
                          :new-lbl  new-lbl}))

(defn update-secret [group-id current-lbl new-val-enc]
  (db/update-secret-val! {:group-id group-id
                          :lbl      current-lbl
                          :val-enc  new-val-enc}))

(defn do-update-secret [request group-id current-lbl new-lbl new-secret]
  (let [user (auth/user-logged-in? request)
        group (groups/pass-group-exists-by-id? (:id user) group-id)
        update-new-lbl (update-lbl? current-lbl new-lbl)
        update-new-secret (update-secret? new-secret)]

    (if (empty? group)
      (response/found "/pass/groups")                       ;;The group is no longer available, go back to groups
      (let [[msg error] (cond

                          (or (nil? current-lbl)
                              (not (validate-lbl current-lbl))) [nil LBL_NOT_VALID_MSG]

                          (not (lbl-exists? (:id group) current-lbl)) [nil (str "The label " current-lbl " does not exist")]

                          (lbl-exists? (:id group) new-lbl) [nil (str "The label " new-lbl " already exists")]

                          (and update-new-secret update-new-lbl) (do
                                                                   (update-lbl-and-secret (:id group) current-lbl new-lbl (encode-secret group user (utils/ensure-bytes new-secret)))
                                                                   ["Updated label and secret" nil])

                          update-new-lbl (do
                                           (update-lbl (:id group) current-lbl new-lbl)
                                           ["Updated label" nil])
                          update-new-secret (do
                                              (update-secret (:id group) current-lbl (encode-secret group user (utils/ensure-bytes new-secret)))
                                              ["Updated secret" nil])
                          :else
                          [nil nil])

            ]

        (found request msg (when error [error]))))))

(defn create-secret [request group-id lbl secret]
  {:pre [group-id lbl secret]}
  (let [user (auth/user-logged-in? request)
        group (groups/pass-group-exists-by-id? (:id user) group-id)
        lbl' (StringUtils/trimToNull (str lbl))

        secret-count (groups/total-user-secret-count (:id user))]

    (if (empty? group)
      (response/found "/pass/groups")                       ;;The group is no longer available, go back to groups
      (let [[msg error] (cond
                          (or (nil? lbl')
                              (not (validate-lbl lbl'))) [nil LBL_NOT_VALID_MSG]
                          (lbl-exists? (:id group) lbl') [nil (str "The secret " lbl' " already exist")]

                          (not (< secret-count (user-limits/max-secrets user))) [nil "Total secrets limit reached, please upgrade your current plan"]

                          :else
                          (do
                            (insert-secret (:id group) lbl' (encode-secret group user (utils/ensure-bytes secret)))
                            ["Secret created" nil]))

            ]

        (found request msg (when error [error]))))))

(defn decrypt-secret [{:keys [enc-key]} {:keys [user-group-master-key-enc]} {:keys [val-enc]}]
  {:pre [enc-key user-group-master-key-enc val-enc]}

  (if val-enc
    (keys/decrypt
      (keys/decrypt
        enc-key
        user-group-master-key-enc)
      val-enc)
    (throw (RuntimeException. (str "val-enc cannot be null here")))))

(defn remove-secret [request group-id lbl]
  {:pre [group-id lbl]}
  (let [user (auth/user-logged-in? request)
        group (groups/pass-group-exists-by-id? (:id user) group-id)
        lbl' (StringUtils/trimToNull (str lbl))]

    (if (empty? group)
      (response/found "/pass/groups")                       ;;The group is no longer available, go back to groups
      (let [[msg error] (cond
                          (or (nil? lbl')) [nil "The label provided is not valid"]
                          (not (lbl-exists? (:id group) lbl')) [nil (str "The secret " lbl' " does not exist")]
                          :else
                          (do
                            (db/delete-secret! {:group-id (:id group) :lbl lbl})
                            [(str "Secret " lbl' " removed") nil]))

            ]

        (found request msg (when error [error]))))))

(defn view [group-id {:keys [flash] :as request} & {:keys [show-lbl show-secret]}]
  {:keys [group-id]}
  (let [user (auth/user-logged-in? request)
        group (groups/pass-group-exists-by-id? (:id user) group-id)
        secret-labels (when (not-empty group) (get-group-secrets (:id group) :show-lbl show-lbl :show-secret show-secret))

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
        "dashboard/secrets/main.html"
        (merge
          {:pass-groups    (groups-cached/query-pass-groups-enriched-cached (:id user))
           :group          group
           :user           user
           :user-name      (:name user)
           :secrets        secret-labels
           :view           "secrets"
           :view-id        (:secrets pass-group-events/VIEW-IDS)

           :secrets-active "active"
           :show-page-help false
           :hint-wizzard   show-wizz
           }
          (select-keys flash [:name :message :errors]))))))


(defn do-show-secret [request group-id lbl]
  {:pre [group-id lbl]}
  (let [user (auth/user-logged-in? request)
        group (groups/pass-group-exists-by-id? (:id user) group-id)
        lbl' (StringUtils/trimToNull (str lbl))
        lbl-record (lbl-exists? (:id group) lbl')]

    (if (empty? group)
      (response/found "/pass/groups")                       ;;The group is no longer available, go back to groups
      (let [[secret error] (cond
                             (or (nil? lbl')) [nil "The label provided is not valid"]
                             (not (lbl-exists? (:id group) lbl')) [nil (str "The secret " lbl' " does not exist")]
                             :else
                             [(decrypt-secret user group lbl-record) nil])

            ]

        (if secret
          (view group-id request :show-lbl lbl' :show-secret (utils/ensure-str secret))
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
                              (pass-group-events/notify-group-secret-change group-id)
                              (create-secret request group-id (-parse-str-input (:lbl params)) (-parse-str-input (:secret params))))
        (= action "remove") (do
                              (pass-group-events/notify-group-secret-change group-id)
                              (remove-secret request group-id (-parse-str-input (:lbl params))))
        (= action "show") (do-show-secret request group-id (-parse-str-input (:lbl params)))
        (= action "update") (do
                              (pass-group-events/notify-group-secret-change group-id)
                              (do-update-secret request group-id
                                                (-parse-str-input (:current-lbl params))
                                                (-parse-str-input (:new-lbl params))
                                                (-parse-str-input (:new-secret params))))


        :else
        (do
          (error "Action not supported " action)
          (RuntimeException. (str "Action " action " not implemented")))))
    (catch Exception e
      (error e)
      (found request nil [(str "Internal error while updating groups: " e)]))))
