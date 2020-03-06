(ns
  ^{:doc "Send inserts to user_visits and other events
   This namespace buffers the inserts so try and not affect user navigation
   "}
  keyssrv.routes.user-events
  (:require [keyssrv.db.core :as db]
            [clojure.core.async :as async]
            [fun-utils.core :as fun-utils]
            [mount.core :as mount]
            [clojure.tools.logging :as log]))

(def EVENTS-REF {"load" 1
                 "registered" 2
                 "wizz-started" 3
                 "wizz-plan" 4
                 "wizz-confirm" 5
                 "wizz-skip" 6
                 "recapcha-fail" 20})


(defn translate-event [event]
     (if (number? event)
       event
       (or
         (get EVENTS-REF event)
         -1)))

(defn trunc
  [s n]
  (subs s 0 (min (count s) n)))


(defn _insert-user-visit! [user event url ref-url]
  {:pre [(:id user) (string? url) (or (string? ref-url) (nil? ref-url))]}
  (db/create-user-visit! {:user-id (:id user)
                          :event (or (translate-event event) 0)
                          :url     url
                          :ref-url (when ref-url
                                     (trunc ref-url 300))}))


(defn _insert-visits-batch! [visits]
  (try
    (doseq [{:keys [user event url ref-url]} visits]
      (db/with-transaction
        (fn [_]
          (when user
            (_insert-user-visit! user event url ref-url)))))
    (catch Exception e (log/error e))))


(mount/defstate VISIT_SENDER
                :start (let [ch (async/chan (async/sliding-buffer 500))
                             buff (fun-utils/buffered-chan ch 100 1000)]

                         (fun-utils/thread-seq
                           _insert-visits-batch!
                           buff)

                         {:ch ch
                          :buff buff})

                :stop (when (:ch VISIT_SENDER)
                        (try
                          (async/close! (:ch VISIT_SENDER))
                          (catch Exception _))))

(defn user-visit!
  ([user event url]
   (user-visit! user event url nil))
  ([user event url ref-url]
   (when user
     (async/>!! (:ch VISIT_SENDER) {:user user :event event :url url :ref-url ref-url}))))

(defn get-user-visits [user]
  {:pre [(:id user)]}
  (db/get-user-visits {:user-id (:id user)}))