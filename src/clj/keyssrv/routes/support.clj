(ns keyssrv.routes.support
    (:require [keyssrv.layout :as layout]
      [keyssrv.users.auth :as auth]
      [keyssrv.config :as conf]
      [ring.util.http-response :as response]
      [keyssrv.notification.notify :as notify]
      [keyssrv.utils :as utils]))


(defn found [req message errors]
      (assoc (response/found (str (:uri req) (when (:query-string req) (str "?" (:query-string req)))))
             :flash {:errors  errors
                     :message message}))

(defn view [{:keys [flash] :as request}]
      (let [user (auth/user-logged-in? request)]

           (layout/render* request
                           "support/support.html"
                           (merge
                             {:user           user
                              :user-name      (:user-name user)
                              :support-active "active"}
                             (select-keys flash [:name :message :errors])))))

(defn send-support-ticket-email [user txt]
      (notify/send-support-ticket-email (:support-ticket-email conf/env) user txt))

(defn create-contact-ticket [{:keys [params] :as request} user]
  (let [request' (assoc request :uri "/contact")
        txt (utils/ensure-str (:ticket-text params))

        ;; either use the user's logged in email, or the email from provided form the post form
        email (or (:email user)
                  (utils/ensure-str (:email params)))

        user' (-> user
                  (assoc :email email)
                  (update :user-name #(or % email)))]

    (cond
      (not txt) (found request' nil "Please provide a message")
      (not email) (found request' nil "Please provide a contact email address")
      :else (do
              (send-support-ticket-email user' txt)
              (found request' "Thanks for contacting us. We will respond to your request as promptly as possible." nil)))))

(defn create-support-ticket [{:keys [params] :as request} user]
      (let [txt (utils/ensure-str (:ticket-text params))

            ;; either use the user's logged in email, or the email from provided form the post form
            email (or (:email user)
                      (utils/ensure-str (:email params)))

            user' (-> user
                      (assoc :email email)
                      (update :user-name #(or % email)))]

           (cond
             (not txt) (found request nil "Please provide a description for the support ticket")
             (not email) (found request nil "Please provide a contact email address")
             :else (do
                     (send-support-ticket-email user' txt)
                     (found request "Support ticket created, we'll respond to your request as promptly as possible." nil)))))

(defn create [{:keys [params] :as request}]
      (let [user (or (auth/user-logged-in? request))

            action (:action params)
            ]
           (cond
             (= action "create-ticket") (create-support-ticket request user)
             (= action "create-contact") (create-contact-ticket request user)

             :else
             (throw (RuntimeException. (str "Action: " action " not recognised"))))))