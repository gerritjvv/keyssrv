(ns
  ^{:doc "
  group environments

  "}
  keyssrv.routes.groups.envs
  (:require [keyssrv.users.auth :as auth]
            [keyssrv.groups.core :as groups]
            [keyssrv.routes.sse.pass-group-events :as pass-group-events]
            [keyssrv.db.core :as db]
            [ring.util.http-response :as response]
            [keyssrv.layout :as layout]
            [keyssrv.users.limits :as user-limits]
            [keyssrv.utils :as utils]
            [keyssrv.compress :as c]
            [keyssrv.routes.groups.secrets :as group-secrets]
            [clojure.tools.logging :refer [error]]
            [keyssrv.groups.cache :as groups-cached]
            [keyssrv.routes.index.wizzards :as wizzards]
            [keyssrv.routes.groups.wizz :as group-wizz]
            [keyssrv.routes.sse.pass-group-events :as pass-group-events])
  (:import (org.apache.commons.lang3 StringUtils)
           (java.util UUID)))


(defn -parse-str-input [v]
  (StringUtils/trimToNull (str v)))

(defn found [req message errors]
  (assoc (response/found (str (:uri req) "?" (:query-string req)))
    :flash {:errors  errors
            :message message}))

(defn get-group-envs-raw [group-id]
  (db/select-group-envs-lbls {:group-id group-id}))

(defn get-group-envs-by-lbls [group-id lbls]
  (let [lbls' (filter
                group-secrets/validate-lbl
                lbls)]
    (when (seq lbls')
      (db/select-group-envs-by-lbls {:group-id group-id :lbls lbls'}))))

(defn get-group-envs
  "Only lbl and id is returned, values are not"
  [group-id]
  (map
    (fn [{:keys [id gid lbl description] :as r}]
      {:id  id
       :gid gid
       :lbl lbl
       :description description})
    (get-group-envs-raw group-id)))

(defn group-env-exists-by-id?
  "Only returns the id, lbl and description"
  [group-id id]
  {:pre [(number? group-id) id (instance? UUID id)]}
  (let [record (db/get-group-env-lbl-by-id {:group-id group-id :gid id})]

    (when (not-empty record)
      record)))

(defn get-whole-env-by-id
  "Only returns the id lbl description and val-enc"
  [group-id id]
  {:pre [id]}
  (let [record (db/get-group-env-by-id {:group-id group-id :gid id})]
    (when (not-empty record)
      record)))


(defn insert-group-env [group-id lbl val-enc description]
  (db/insert-group-env! {:group-id group-id
                         :lbl      lbl
                         :val-enc  (utils/ensure-bytes val-enc)
                         :description description}))



(defn update-group-env [group-id group-env-id lbl val-enc description]
  (db/update-group-env! {:group-id group-id
                         :id      group-env-id
                         :lbl     lbl
                         :val-enc val-enc
                         :description description}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; route/public functions


(defn do-update-group-env [request group-id group-env-id lbl val description]
  (let [user (auth/user-logged-in? request)
        group (groups/pass-group-exists-by-id? (:id user) group-id)

        group-env (when (not-empty group) (group-env-exists-by-id? (:id group) group-env-id))]

    (if (empty? group)
      (response/found "/pass/groups")                       ;;The group is no longer available, go back to groups
      (let [[msg error] (cond

                          (not group-env) [nil (str "The env does not exist")]

                          (not (and lbl (group-secrets/validate-lbl lbl))) [nil (str group-secrets/LBL_NOT_VALID_MSG)]
                          :else
                          (try
                            (update-group-env (:id group)
                                              (:id group-env)
                                              lbl
                                              (group-secrets/encode-secret group user (utils/ensure-bytes (c/compress val)))
                                              description)
                            ["Updated env" nil]
                            (catch Throwable e
                              (if (utils/already-exist-exception? e)
                                [nil (str "The env " lbl " already exist")]
                                (throw e)))))

            ]

        (found request msg (when error [error]))))))

(defn create-group-env [request group-id lbl val description]
  {:pre [group-id]}
  (let [user (auth/user-logged-in? request)
        group (groups/pass-group-exists-by-id? (:id user) group-id)

        env-count (groups/total-user-env-count (:id user))]

    (if (empty? group)
      (response/found "/pass/groups")                       ;;The group is no longer available, go back to groups
      (let [[msg error] (cond

                          (not (and lbl (group-secrets/validate-lbl lbl))) [nil (str group-secrets/LBL_NOT_VALID_MSG)]

                          (not (< env-count (user-limits/max-envs user))) [nil "Total env limit reached, please upgrade your current plan"]
                          :else
                          (try
                            (insert-group-env (:id group) lbl
                                              (group-secrets/encode-secret group user
                                                                           (utils/ensure-bytes (c/compress val)))
                                              description)
                            ["Env created" nil]
                            (catch Throwable e
                              (if (utils/already-exist-exception? e)
                                [nil (str "The env " lbl " already exist")]
                                (throw e)))))

            ]

        (found request msg (when error [error]))))))

(defn remove-group-env [request group-id group-env-id]
  {:pre [group-id group-env-id]}
  (let [user (auth/user-logged-in? request)
        group (groups/pass-group-exists-by-id? (:id user) group-id)]

    (if (empty? group)
      (response/found "/pass/groups")                       ;;The group is no longer available, go back to groups
      (let [env (group-env-exists-by-id? (:id group) group-env-id)
            [msg error] (cond
                          (nil? env) [nil "The env provided is not valid"]

                          :else
                          (do
                            (db/delete-group-env-by-id! {:group-id (:id group) :id (:id env)})
                            [(str "Env removed") nil]))

            ]

        (found request msg (when error [error]))))))

(defn view [group-id {:keys [flash] :as request} & {:keys [show-id show-lbl show-val show-description]}]
  {:keys [group-id]}
  (let [user (auth/user-logged-in? request)
        group (groups/pass-group-exists-by-id? (:id user) group-id)
        envs (when (not-empty group) (get-group-envs (:id group)))

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
        "dashboard/envs/main.html"
        (merge
          {:pass-groups       (groups-cached/query-pass-groups-enriched-cached (:id user))
           :group             group
           :user              user
           :user-name         (:name user)
           :envs              envs

           ;; causes the env by show-id and show-val to be shown for edit to the user
           :show-id           show-id
           :show-lbl          show-lbl
           :show-val          show-val
           :show-description  show-description
           :show-update-modal (if show-id true false)
           :view              "envs"
           :envs-active       "active"
           :view-id           (:envs pass-group-events/VIEW-IDS)

           :show-page-help    false
           :hint-wizzard      show-wizz
           }
          (select-keys flash [:name :message :errors]))))))


(defn do-show-env [request group-id group-env-id]
  {:pre [group-id group-env-id]}
  (let [user (auth/user-logged-in? request)
        group (groups/pass-group-exists-by-id? (:id user) group-id)
        record (when (not-empty group) (get-whole-env-by-id (:id group) group-env-id))]

    (if (empty? group)
      (response/found "/pass/groups")                       ;;The group is no longer available, go back to groups
      (let [[val error] (cond
                          (empty? record) [nil "Env entry does not exist"]
                          :else
                          [(c/decompress (group-secrets/decrypt-secret user group record))
                           nil])
            ]

        (if val
          (view group-id request :show-id group-env-id
                :show-lbl (utils/ensure-str (:lbl record))
                :show-val (utils/ensure-str val)
                :show-description (utils/ensure-str (:description record)))
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
                              (pass-group-events/notify-group-env-change group-id)
                              (create-group-env request group-id
                                                (-parse-str-input (:lbl params))
                                                (-parse-str-input (:val params))
                                                (-parse-str-input (:description params))))
        (= action "remove") (do
                              (pass-group-events/notify-group-env-change group-id)
                              (remove-group-env request group-id (utils/ensure-uuid (:group-env-id params))))
        (= action "show") (do-show-env request group-id (utils/ensure-uuid (:group-env-id params)))
        (= action "update") (do
                              (pass-group-events/notify-group-env-change group-id)
                              (do-update-group-env request group-id
                                                   (utils/ensure-uuid (:group-env-id params))
                                                   (-parse-str-input (:new-lbl params))
                                                   (-parse-str-input (:new-val params))
                                                   (-parse-str-input (:new-description params))))


        :else
        (do
          (prn ">>>>>!!!!!!!!!!!!!!!!!!!!! action => " action)
          (error "Action not supported " action)
          (RuntimeException. (str "Action " action " not implemented")))))
    (catch Exception e
      (error e)
      (found request nil [(str "Internal error while updating groups: " e)]))))
