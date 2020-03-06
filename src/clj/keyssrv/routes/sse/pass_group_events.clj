(ns
  ^{:doc "SSE events for any passgroup items"}
  keyssrv.routes.sse.pass-group-events
  (:require [keyssrv.tokens.core :as tokens]
            [keyssrv.layout :as layout]
            [keyssrv.utils :as utils]))

(def VIEW-IDS {:users    1
               :certs    2
               :secrets  3
               :snippets 4
               :logins   5
               :envs     6
               :dbs      7})


(defn sse-id [group-id view-id]
  (str "sse/" group-id "/" view-id))

(defn notify-group-data-change [group-id view-id]
  (tokens/set-token
    group-id
    (sse-id group-id view-id)
    (System/currentTimeMillis)
    20))

(defn get-group-data-change-ts [group-id view-id]
  (tokens/get-token
    group-id
    (sse-id group-id view-id)))

(defn notify-group-user-change [group-id]
  (notify-group-data-change group-id (:users VIEW-IDS)))

(defn notify-group-secret-change [group-id]
  (notify-group-data-change group-id (:secrets VIEW-IDS)))


(defn notify-group-certs-change [group-id]
  (notify-group-data-change group-id (:certs VIEW-IDS)))

(defn notify-group-snippet-change [group-id]
  (notify-group-data-change group-id (:snippets VIEW-IDS)))

(defn notify-group-login-change [group-id]
  (notify-group-data-change group-id (:logins VIEW-IDS)))

(defn notify-group-db-change [group-id]
  (notify-group-data-change group-id (:dbs VIEW-IDS)))

(defn notify-group-env-change [group-id]
  (notify-group-data-change group-id (:envs VIEW-IDS)))


(defn query-refresh-data [{:keys [params headers] :as req}]
  (let [{:keys [gid v ts]} params
        {:strs [last-event-id]} headers

        gid (utils/ensure-str gid)
        v (utils/ensure-int v)
        ts (utils/ensure-int ts)
        last-event-id (utils/ensure-int last-event-id)

        last-id (max last-event-id ts)

        event-update-ts (or
                          (get-group-data-change-ts gid v)
                          last-id)]

    ;(prn {:headers         headers
    ;      :params          params
    ;
    ;      :last-id         last-id
    ;      :event-update-ts event-update-ts
    ;      :data-change-ts  (get-group-data-change-ts gid v)})


    (if (> event-update-ts last-id)
      (layout/sse-event req {:id    event-update-ts
                             :event "refresh"
                             :data  "1"}
                        :end true)
      (layout/sse-event req {:id    event-update-ts
                             :retry 10000
                             :event "refresh"
                             :data  "0"}))))
