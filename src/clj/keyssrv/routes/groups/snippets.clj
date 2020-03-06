(ns
  ^{:doc "group snippets


  "}
  keyssrv.routes.groups.snippets
  (:require [keyssrv.users.auth :as auth]
            [keyssrv.groups.core :as groups]
            [keyssrv.routes.sse.pass-group-events :as pass-group-events]
            [keyssrv.db.core :as db]
            [ring.util.http-response :as response]
            [keyssrv.users.limits :as user-limits]
            [keyssrv.layout :as layout]
            [keyssrv.utils :as utils]
            [keyssrv.compress :as c]
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

(defn get-group-snippets-raw [group-id]
  (db/select-group-snippets-titles {:group-id group-id}))


(defn get-group-snippets-by-titles [group-id titles]
  (let [lbls' (filter
                group-secrets/validate-lbl
                titles)]
    (when (seq lbls')
      (db/get-group-snippet-by-title {:group-id group-id :titles lbls'}))))

(defn get-group-snippets [group-id]
  (map
    (fn [{:keys [id gid title] :as r}]
      {:id    id
       :gid   gid
       :title title})
    (get-group-snippets-raw group-id)))

(defn group-snippet-exists-by-id?
  "Only returns the id and title"
  [group-id id]
  {:pre [id]}
  (let [record (db/get-group-snippet-title-by-gid {:group-id group-id :gid id})]
    (when (not-empty record)
      record)))

(defn get-whole-snippet-by-id
  "Only returns the id title and val-enc"
  [group-id id]
  {:pre [id]}
  (let [record (db/get-group-snippet-by-gid {:group-id group-id :gid id})]
    (when (not-empty record)
      record)))

(defn insert-group-snippet [group-id title val-enc]
  (db/insert-group-snippet! {:group-id group-id
                             :title    title
                             :val-enc  (utils/ensure-bytes val-enc)}))



(defn update-group-snippet [group-id group-snippet-id title val-enc]
  (db/update-group-snippet! {:group-id group-id
                             :id       group-snippet-id
                             :title    title
                             :val-enc  val-enc}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; route/public functions


(defn do-update-group-snippet [request group-id group-snippet-id title val]
  (let [user (auth/user-logged-in? request)
        group (groups/pass-group-exists-by-id? (:id user) group-id)]

    (if (empty? group)
      (response/found "/pass/groups")                       ;;The group is no longer available, go back to groups
      (let [group-login (when (not-empty group) (group-snippet-exists-by-id? (:id group) group-snippet-id))
            [msg error] (cond

                          (not group-login) [nil (str "The note does not exist")]

                          :else
                          (try
                            (update-group-snippet (:id group)
                                                  (:id group-login)
                                                  title
                                                  (group-secrets/encode-secret group user (utils/ensure-bytes (c/compress val))))
                            ["Updated snippet" nil]
                            (catch Throwable e
                              (if (utils/already-exist-exception? e)
                                [nil (str "The note " title " already exist")]
                                (throw e)))))

            ]

        (found request msg (when error [error]))))))

(defn create-group-snippet [request group-id title val]
  {:pre [group-id]}
  (let [user (auth/user-logged-in? request)
        group (groups/pass-group-exists-by-id? (:id user) group-id)

        snippet-count (when (not-empty group) (groups/total-user-snippet-count (:id user)))]

    (if (empty? group)
      (response/found "/pass/groups")                       ;;The group is no longer available, go back to groups
      (let [[msg error] (cond

                          (not (< snippet-count (user-limits/max-snippets user))) [nil "Total note limit reached, please upgrade your current plan"]
                          :else
                          (try
                            (insert-group-snippet (:id group) title
                                                  (group-secrets/encode-secret group user
                                                                               (utils/ensure-bytes (c/compress val))))
                            ["Note created" nil]
                            (catch Throwable e
                              (if (utils/already-exist-exception? e)
                                [nil (str "The note " title " already exist")]
                                (throw e)))))

            ]

        (found request msg (when error [error]))))))

(defn remove-group-snippet [request group-id group-snippet-id]
  {:pre [group-id group-snippet-id]}
  (let [user (auth/user-logged-in? request)
        group (groups/pass-group-exists-by-id? (:id user) group-id)]

    (if (empty? group)
      (response/found "/pass/groups")                       ;;The group is no longer available, go back to groups
      (let [
            snippet (get-whole-snippet-by-id (:id group) group-snippet-id)
            [msg error] (cond
                          (nil? group-snippet-id) [nil "The note provided is not valid"]
                          (empty? snippet)        [nil "The note provided is not valid"]

                          :else
                          (do
                            (db/delete-group-snippet-by-id! {:group-id (:id group) :id (:id snippet)})
                            [(str "Note removed") nil]))

            ]

        (found request msg (when error [error]))))))

(defn view [group-id {:keys [flash] :as request} & {:keys [show-id show-title show-val]}]
  {:keys [group-id]}
  (let [user (auth/user-logged-in? request)
        group (groups/pass-group-exists-by-id? (:id user) group-id)
        snippets (when (not-empty group) (get-group-snippets (:id group)))

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
        "dashboard/notes/main.html"
        (merge
          {:pass-groups       (groups-cached/query-pass-groups-enriched-cached (:id user))
           :group             group
           :user              user
           :user-name         (:name user)
           :snippets          snippets

           ;; causes the snippet by show-id and show-val to be shown for edit to the user
           :show-id           show-id
           :show-title        show-title
           :show-val          show-val
           :show-update-modal (if show-id true false)

           :view              "snippets"
           :view-id           (:snippets pass-group-events/VIEW-IDS)

           :snippets-active   "active"
           :show-page-help    false

           :hint-wizzard      show-wizz
           }
          (select-keys flash [:name :message :errors]))))))


(defn do-show-snippet [request group-id group-snippet-id]
  {:pre [group-id group-snippet-id]}
  (let [user (auth/user-logged-in? request)
        group (groups/pass-group-exists-by-id? (:id user) group-id)
        record (when (not-empty group) (get-whole-snippet-by-id (:id group) group-snippet-id))]

    (if (empty? group)
      (response/found "/pass/groups")                       ;;The group is no longer available, go back to groups
      (let [[val error] (cond
                          (empty? record) [nil "Note does not exist"]
                          :else
                          [(c/decompress (group-secrets/decrypt-secret user group record)) nil])

            ]

        (if val
          (view group-id request :show-id group-snippet-id :show-title (:title record) :show-val (utils/ensure-str val))
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
                              (pass-group-events/notify-group-snippet-change group-id)
                              (create-group-snippet request group-id
                                                    (-parse-str-input (:title params))
                                                    (-parse-str-input (:val params))))
        (= action "remove") (do
                              (pass-group-events/notify-group-snippet-change group-id)
                              (remove-group-snippet request group-id (utils/ensure-uuid (:group-snippet-id params))))
        (= action "show") (do-show-snippet request group-id (utils/ensure-uuid (:group-snippet-id params)))
        (= action "update") (do
                              (pass-group-events/notify-group-snippet-change group-id)
                              (do-update-group-snippet request group-id
                                                       (utils/ensure-uuid (:group-snippet-id params))
                                                       (-parse-str-input (:new-title params))
                                                       (-parse-str-input (:new-val params))))

        :else
        (do
          (error "Action not supported " action)
          (RuntimeException. (str "Action " action " not implemented")))))
    (catch Exception e
      (error e)
      (found request nil [(str "Internal error while updating groups: " e)]))))
