(ns

  ^{:doc "Sending emails"}
  keyssrv.notification.email
  (:require
    [keyssrv.config :as config]
    [keyssrv.utils :as utils]
    [postal.core :as postal]
    [mount.core :as mount]
    [clojure.core.async :as async]
    [fun-utils.core :as fun-utils]
    [clojure.tools.logging :as log]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; public functions

(defn required-smtp-env [k]
      (if-let [v (utils/ensure-str (get-in config/env [:smtp k]))]
              v
              (throw (RuntimeException. (str "Require smtp " k " but not defined")))))

(defn required-int-smtp-env [k]
      (if-let [v (utils/ensure-int (get-in config/env [:smtp k]))]
              v
              (throw (RuntimeException. (str "Require smtp " k " but not defined")))))

(defn is-test? []
      (not= ":test" (str (:env config/env))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; public functions

(mount/defstate Mail
                :start {:host "mail.smtp2go.com"            ; (required-smtp-env :host)
                        :user (required-smtp-env :user)
                        :pass (required-smtp-env :password)
                        :port (required-int-smtp-env :port)
                        :ssl  (or (get-in config/env [:smtp :ssl]) true)
                        })


(defn _send-emails [emails]
  (doseq [email emails]
    (let [{:keys [code] :as res} (postal/send-message Mail email)]
      (if (zero? code)
        res
        (log/error res)))))

(mount/defstate MAIL_BUFFER
                :start (let [ch (async/chan 500)
                             buff (fun-utils/buffered-chan ch 100 1000)]

                         (fun-utils/thread-seq
                           _send-emails
                           buff)

                         {:ch ch
                          :buff buff})

                :stop (when (:ch MAIL_BUFFER)
                        (try
                          (async/close! (:ch MAIL_BUFFER))
                          (catch Exception _))))

(defn send-mail [{:keys [from to subject body] :as email}]
      {:pre [from to subject body]}
  (async/>!! (:ch MAIL_BUFFER) email))
