(ns
  ^{:doc "Using email.clj to send notifications"}
  keyssrv.notification.notify
  (:require [keyssrv.notification.email :as email]
            [selmer.parser :as parser]
            [mount.core :as mount]))

(defonce NO-REPLY "noreply@pkhub.io")


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; protocols

(defprotocol ITrace
  (-get-reset-codes-email [this] "Returns :to :ks or the last email sent")
  (-get-reset-email [this] "Returns :to :url of the last email sent")
  (-get-verification-code-email [this] "Returns :to :code of the last email sent")
  (-get-register-confirm-email [this] "Returns :to :url of the last email sent"))

(defprotocol INotifier
  (-send-group-share-email [this owner user group])
  (-send-support-ticket-email [this to user txt])
  (-send-welcome-email [this to user])
  (-send-reset-codes-email [this to ks])
  (-send-reset-email [this to url])
  (-send-verification-code-email [this to code])
  (-send-register-confirm-email [this to k]))

(defn test-notifier
  "See keyssrv.test.utils/setup"
  []
  (let [reset-codes-email (atom nil)
        reset-email (atom nil)
        verification-code-email (atom nil)
        register-confirm-email (atom nil)]

    (reify

      ITrace
      (-get-reset-codes-email [_] @reset-codes-email)
      (-get-reset-email [_] @reset-email)
      (-get-verification-code-email [_] @verification-code-email)
      (-get-register-confirm-email [_] @register-confirm-email)

      INotifier
      (-send-welcome-email [_ to user]
        (prn "Sending welcome " {:to to :user user}))

      (-send-group-share-email [_ owner user group]
        (prn "Sending share email " {:owner (:user-name owner) :user (:user-name user) :group (:name group)}))

      (-send-support-ticket-email [_ to user txt]
        (prn "Sending support ticket " {:to to :user user :txt txt}))

      (-send-reset-codes-email [_ to ks]
        (reset! reset-codes-email {:to to :ks ks}))

      (-send-reset-email [_ to url]
        (reset! reset-email {:to to :url url})
        (prn "For test and dev we send: " (parser/render-file "email_reset.tmpl" {:url url})))

      (-send-verification-code-email [_ to code]
        (reset! verification-code-email {:to to :code code})
        (prn "For test and dev we send: " (parser/render-file "email_verification_code.tmpl" {:code code})))

      (-send-register-confirm-email [_  to k]
        (reset! register-confirm-email {:to to :k k})
        (prn "For test and dev we send: " (parser/render-file "mailto/confirmemail/email_register.tmpl" {:k k}))))))



(mount/defstate NOTIFIER
                :start (reify INotifier
                         (-send-support-ticket-email [_ to user txt]
                           (email/send-mail
                             {:from (:email user)
                              :to to
                              :subject (str "PKHub.io Ticket for " (:user-name user))
                              :body
                              (parser/render-file "email_support_ticket.tmpl" {:txt txt
                                                                               :user user})}))

                         (-send-group-share-email [_ owner user group]
                           (email/send-mail {:from    NO-REPLY
                                             :to      (:email user)
                                             :subject (str  "Safe share from " (:email user) " (PKHub.io)")
                                             :body    (parser/render-file "email_groupshare.tmpl" {:owner owner
                                                                                                   :user user
                                                                                                   :group group})}))

                         (-send-reset-codes-email [_ to ks]
                           (throw (RuntimeException. (str "Do not send reset codes via email"))))

                         (-send-reset-email [_ to url]
                           (email/send-mail {:from    NO-REPLY
                                             :to      to
                                             :subject "Reset link (PKHub.io)"
                                             :body    (parser/render-file "email_reset.tmpl" {:url url})}))

                         (-send-verification-code-email [_ to code]
                           (email/send-mail {:from    NO-REPLY
                                             :to      to
                                             :subject "Verification code (PKHub.io)"
                                             :body    (parser/render-file "email_verification_code.tmpl" {:code code})}))

                         (-send-register-confirm-email [_ to k]
                           (email/send-mail {:from    NO-REPLY
                                             :to      to
                                             :subject "Please verify your PKHub.io account"

                                             :body
                                                      [:alternative
                                                       {:type "text/plain"
                                                        :content (parser/render-file "mailto/confirmemail/email_register.tmpl" {:k k})}
                                                       {:type "text/html"
                                                        :content (parser/render-file "mailto/confirmemail/index.html" {:k k})}
                                                       ]
                                                      }))

                         (-send-welcome-email [_ to user]
                           (email/send-mail {:from    NO-REPLY
                                             :to      to
                                             :subject "Welcome to PKHub.io"

                                             :body
                                                      [:alternative
                                                       {:type "text/plain"
                                                        :content (parser/render-file "mailto/welcomeemail/email_welcome.tmpl" {:user user})}
                                                       {:type "text/html"
                                                        :content (parser/render-file "mailto/welcomeemail/index.html" {:user user})}
                                                       ]
                                             }))

                         ))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; public functions

; We should not send the reset codes to email ever.
;(defn send-reset-codes-email [to ks]
;  {:pre [to ks]}
;  (-send-reset-codes-email NOTIFIER to ks))

(defn send-reset-email [to url]
  {:pre [to url]}
  (-send-reset-email NOTIFIER to url))

(defn send-verification-code-email [to code]
  {:pre [to code]}
  (-send-verification-code-email NOTIFIER to code))

(defn send-register-confirm-email [to k]
  {:pre [to k]}
  (-send-register-confirm-email NOTIFIER to k))

(defn send-welcome-email [to user]
  {:pre [to user]}
  (-send-welcome-email NOTIFIER to user))

(defn send-support-ticket-email [to user txt]
  (-send-support-ticket-email NOTIFIER to user txt))

(defn send-group-share-email [owner user group]
  (-send-group-share-email NOTIFIER owner user group))