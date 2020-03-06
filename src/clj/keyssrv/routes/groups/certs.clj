(ns
  ^{:doc "
  group public/private key certificates
  Throughout the file val or new-val is used to refer to the private key
   val is a generic name for all encrypted values stored as val-enc,
   doing this keeps the architecture similar and allows re-use.

   see: snippets
  "}
  keyssrv.routes.groups.certs
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
            [keyssrv.users.registration :as user-registration]
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

(defn get-group-certs-raw [group-id]
  (db/select-group-certs-lbls {:group-id group-id}))

(defn get-group-certs-by-lbls [group-id lbls]
  (let [lbls' (filter group-secrets/validate-lbl lbls)]
    (when (seq lbls')
      (db/select-group-certs-by-lbls {:group-id group-id :lbls lbls'}))))

(defn get-group-certs
  "Returns only id a lbl"
  [group-id]
  (map
    (fn [{:keys [id gid lbl]}]
      {:id  id
       :gid gid
       :lbl lbl})
    (get-group-certs-raw group-id)))

(defn group-cert-exists-by-id?
  "Only returns the id and lbl"
  [group-id id]
  {:pre [id]}
  (let [record (db/get-group-cert-lbl-by-id {:group-id group-id :gid id})]
    (when (not-empty record)
      record)))

(defn get-whole-cert-by-id
  "Only returns the id lbl and val-enc"
  [group-id id]
  {:pre [id]}
  (let [record (db/get-group-cert-by-id {:group-id group-id :gid id})]
    (when (not-empty record)
      record)))


(defn insert-group-cert [group-id user-assigned-record lbl pub-key-comp val-enc]
  {:pre [group-id]}
  (db/insert-group-cert! {:group-id      group-id
                          :user-assigned (:id user-assigned-record) ;;optional, default null
                          :lbl           lbl
                          :pub-key-comp  pub-key-comp
                          :val-enc       (utils/ensure-bytes val-enc)}))



(defn update-group-cert [group-id group-cert-id lbl pub-key-comp val-enc]
  (db/update-group-cert! {:group-id     group-id
                          :id           group-cert-id
                          :lbl          lbl
                          :pub-key-comp pub-key-comp
                          :val-enc      val-enc}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; route/public functions


(defn do-update-group-cert [request group-id group-cert-id lbl pub-key priv-key]
  {:pre [group-id]}
  (let [user (auth/user-logged-in? request)
        group (groups/pass-group-exists-by-id? (:id user) group-id)

        group-cert (group-cert-exists-by-id? (:id group) group-cert-id)

        assigned-user-id (:assigned-to group-cert)
        assigned-user-record (when assigned-user-id (user-registration/get-user-by-user-id assigned-user-id))]

    (if (empty? group)
      (response/found "/pass/groups")                       ;;The group is no longer available, go back to groups
      (let [[msg error] (cond

                          (not group-cert) [nil (str "The certificate does not exist")]

                          (not (and lbl (group-secrets/validate-lbl lbl))) [nil (str group-secrets/LBL_NOT_VALID_MSG)]

                          (and
                            assigned-user-id
                            (not-empty assigned-user-record)
                            (not= (:id user) assigned-user-id)) [nil (str "This certificate is private to only the assigned user " (:user-name assigned-user-record))]

                          :else
                          (try
                            (update-group-cert (:id group)
                                               (:id group-cert)
                                               lbl
                                               (c/compress pub-key)
                                               (group-secrets/encode-secret group user (utils/ensure-bytes (c/compress priv-key))))
                            ["Updated certificate" nil]
                            (catch Throwable e
                              (if (utils/already-exist-exception? e)
                                [nil (str "The certificate " lbl " already exist")]
                                (throw e)))))

            ]

        (found request msg (when error [error]))))))

(defn create-group-cert [request assigned-user-name group-id lbl pub-key priv-key]
  {:pre [group-id]}
  (let [user (auth/user-logged-in? request)
        group (groups/pass-group-exists-by-id? (:id user) group-id)

        assigned-user-record (when assigned-user-name (user-registration/get-user-by-user-name assigned-user-name))
        assigned-group-rel (when assigned-user-record (groups/group-rel (:id assigned-user-record) (:id group)))

        cert-count (groups/total-user-cert-count (:id user))]

    (if (empty? group)
      (response/found "/pass/groups")                       ;;The group is no longer available, go back to groups
      (let [[msg error] (cond

                          (not (and lbl (group-secrets/validate-lbl lbl))) [nil (str group-secrets/LBL_NOT_VALID_MSG)]

                          (not (< cert-count (user-limits/max-certs user))) [nil "Total certificate limit reached, please upgrade your current plan"]

                          (and
                            assigned-user-name
                            (empty? assigned-user-record)) [nil "The assigned user does not exist"]

                          (and
                            assigned-user-name
                            (not-empty assigned-user-record)
                            (empty? assigned-group-rel)) [nil (str "The assigned user is not in the current Safe " (:name group))]

                          :else
                          (try
                            (insert-group-cert (:id group)
                                               assigned-user-record
                                               lbl
                                               (c/compress pub-key)
                                               (group-secrets/encode-secret group user
                                                                            (utils/ensure-bytes (c/compress priv-key))))
                            ["Certificate created" nil]
                            (catch Throwable e
                              (if (utils/already-exist-exception? e)
                                [nil (str "The certificate " lbl " already exist")]
                                (throw e)))))

            ]

        (found request msg (when error [error]))))))

(defn remove-group-cert [request group-id group-cert-id]
  {:pre [group-id group-cert-id]}
  (let [user (auth/user-logged-in? request)
        group (groups/pass-group-exists-by-id? (:id user) group-id)]

    (if (empty? group)
      (response/found "/pass/groups")                       ;;The group is no longer available, go back to groups
      (let [cert (get-whole-cert-by-id (:id group) group-cert-id)
            [msg error] (cond
                          (nil? group-cert-id) [nil "The certificate provided is not valid"]

                          :else
                          (do
                            (db/delete-group-cert-by-id! {:group-id (:id group) :id (:id cert)})
                            [(str "Certificate removed") nil]))

            ]

        (found request msg (when error [error]))))))

(defn view [group-id {:keys [flash] :as request} & {:keys [show-id show-lbl show-pub-key show-priv-key]}]
  {:keys [group-id]}
  (let [user (auth/user-logged-in? request)
        group (groups/pass-group-exists-by-id? (:id user) group-id)
        certs (when (not-empty group) (get-group-certs (:id group)))

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
        "dashboard/certs/main.html"
        (merge
          {:pass-groups       (groups-cached/query-pass-groups-enriched-cached (:id user))
           :group             group
           :user              user
           :user-name         (:name user)
           :certs             certs

           ;; causes the cert by show-id and show-val to be shown for edit to the user
           :show-id           show-id
           :show-lbl          show-lbl
           :show-pub-key      show-pub-key
           :show-priv-key     show-priv-key
           :show-update-modal (if show-id true false)

           :show-page-help    false

           :view              certs
           :view-id           (:certs pass-group-events/VIEW-IDS)

           :certs-active      "active"
           :hint-wizzard      show-wizz
           }
          (select-keys flash [:name :message :errors]))))))


(defn do-show-cert [request group-id group-cert-id]
  {:pre [group-id group-cert-id]}
  (let [user (auth/user-logged-in? request)
        group (groups/pass-group-exists-by-id? (:id user) group-id)
        record (when (not-empty group) (get-whole-cert-by-id (:id group) group-cert-id))
        assigned-user-id (:assigned-to record)
        assigned-user-record (when assigned-user-id (user-registration/get-user-by-user-id assigned-user-id))
        assigned-group-rel (when assigned-user-record
                             (groups/group-rel assigned-user-id (:id group)))]



    (if (empty? group)
      (response/found "/pass/groups")                       ;;The group is no longer available, go back to groups
      (let [[pub-val val error] (cond
                                  (empty? record) [nil nil "Certificate entry does not exist"]
                                  (and
                                    assigned-user-id
                                    (empty? assigned-user-record)) [nil nil "The assigned user does not exist anymore, please delete the certificate"]

                                  (and
                                    (not-empty assigned-user-record)
                                    (empty? assigned-group-rel)) [nil nil "The assigned user has been removed from the Safe, please delete the certificate"]

                                  (and
                                    (not-empty assigned-user-record)
                                    (not= (:id user) assigned-user-id)) [nil nil (str "This certificate is private to only the assigned user " (:user-name assigned-user-record))]

                                  :else
                                  [(c/decompress (:pub-key-comp record))
                                   (c/decompress (group-secrets/decrypt-secret user group record))
                                   nil])

            ]

        (if val
          (view group-id request :show-id group-cert-id
                :show-lbl (utils/ensure-str (:lbl record))
                :show-pub-key (utils/ensure-str pub-val)
                :show-priv-key (utils/ensure-str val))
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
                              (pass-group-events/notify-group-certs-change group-id)
                              (create-group-cert request
                                                 (-parse-str-input (:assigned-user params))
                                                 group-id
                                                 (-parse-str-input (:lbl params))
                                                 (-parse-str-input (:pub-key params))
                                                 (-parse-str-input (:priv-key params))))
        (= action "remove") (do
                              (pass-group-events/notify-group-certs-change group-id)
                              (remove-group-cert request group-id (utils/ensure-uuid (:group-cert-id params))))
        (= action "show") (do-show-cert request group-id (utils/ensure-uuid (:group-cert-id params)))
        (= action "update") (do
                              (pass-group-events/notify-group-certs-change group-id)
                              (do-update-group-cert request group-id
                                                    (utils/ensure-uuid (:group-cert-id params))
                                                    (-parse-str-input (:new-lbl params))
                                                    (-parse-str-input (:new-pub-key params))
                                                    (-parse-str-input (:new-priv-key params))))


        :else
        (do
          (prn ">>>>>!!!!!!!!!!!!!!!!!!!!! action => " action)
          (error "Action not supported " action)
          (RuntimeException. (str "Action " action " not implemented")))))
    (catch Exception e
      (error e)
      (found request nil [(str "Internal error while updating groups: " e)]))))


