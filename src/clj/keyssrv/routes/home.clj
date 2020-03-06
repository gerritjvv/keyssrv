(ns keyssrv.routes.home
  (:require [keyssrv.routes.users :as user-routes]
            [keyssrv.routes.pass-groups :as pass-groups]
            [keyssrv.routes.settings :as settings]
            [keyssrv.routes.support :as support]
            [keyssrv.routes.index :as index]
            [keyssrv.routes.index.home :as home]
            [keyssrv.routes.pixel :as pixel]
            [schema.core :as s]

            [keyssrv.routes.appkeys :as appkeys]
            [keyssrv.routes.sse.pass-group-events :as pass-group-events]

            [compojure.core :refer [defroutes GET POST DELETE]]
            [compojure.api.sweet :as sweet]
            [keyssrv.utils :as utils]
            [keyssrv.users.auth :as auth]
            [clojure.tools.logging :as log]
            [keyssrv.middleware :as middleware])
  (:import (org.apache.commons.lang3 StringUtils)))

(s/defschema Secret {:lbl s/Str (s/optional-key :val) s/Str})
(s/defschema Secrets [Secret])

(s/defschema Login {(s/optional-key :lbl) s/Str (s/optional-key :login) s/Str (s/optional-key :user-name) s/Str (s/optional-key :user-name2) s/Str (s/optional-key :secret) s/Str})
(s/defschema Logins [Login])

(s/defschema Snippet {:title s/Str (s/optional-key :val) s/Str})
(s/defschema Snippets [Snippet])

(s/defschema Certificate {:lbl s/Str (s/optional-key :pub-key) s/Str (s/optional-key :priv-key) s/Str})
(s/defschema Certificates [Certificate])

(s/defschema Env {:lbl s/Str (s/optional-key :val) s/Str})
(s/defschema Envs [Env])

(s/defschema DB {:lbl s/Str (s/optional-key :type) s/Str (s/optional-key :hosted-on) s/Str
                 ;; db val keys
                 (s/optional-key :host) s/Str (s/optional-key :port) s/Str (s/optional-key :database) s/Str (s/optional-key :dbuser) s/Str (s/optional-key :password) s/Str})

(s/defschema DBs [DB])


(defn split-commas [v]
  (if (string? v)
    (StringUtils/split (str v) \,)
    (mapcat #(StringUtils/split (str %) \,) v)))

(def api-routes
  (sweet/api
    {:swagger
     {:ui   "/api-docs"
      :spec "/swagger.json"

      :data {:info     {:title       "PKHub API"
                        :description "Retrieve group secrets, logins, envs ...."}
             :tags     [{:name "api", :description "Retrieve group secrets, logins, envs ..."}]

             :consumes ["application/json"]
             :produces ["application/json"]}}}

    (sweet/context "/api/v1/safes" []
                   :tags ["api"]
                   :header-params [authorization :- (sweet/describe String "AppKey:AppSecret or Basic: base64(user:pass)")]


                   (sweet/GET "/secret" []
                     :return Secrets
                     :query-params [safe :- String lbls :- [String]]
                     :summary "Return each secret specified by a lbl entry, max 100"
                     (auth/wrap-api-authentication
                                authorization
                                (fn [user]
                                  (pass-groups/api-get-secrets user safe (take 100 (split-commas lbls))))))

                   ;;logins, snippets, cerst, envs

                   (sweet/GET "/logins" []
                     :return Logins
                     :query-params [safe :- String logins :- [String] lbls :- [String]]
                     :summary "Return each login that matches a login entry, max 100, logins will search for the login entry, use lbls to search by lbl"
                     (auth/wrap-api-authentication
                       authorization
                       (fn [user]
                         (pass-groups/api-get-logins user safe :logins (take 100 (split-commas logins))
                                                                        :lbls (take 100 (split-commas lbls))))))

                   (sweet/GET "/snippets" []
                     :return Snippets
                     :query-params [safe :- String lbls :- [String]]
                     :summary "Return each snippet that matches a lbl (title) entry, max 100"
                     (auth/wrap-api-authentication
                       authorization
                       (fn [user] (pass-groups/api-get-snippets user safe (take 100 (split-commas lbls))))))

                   (sweet/GET "/certs" []
                     :return Certificates
                     :query-params [safe :- String lbls :- [String]]
                     :summary "Return each certificate pair that matches a lbl (title) entry, max 100"
                     (auth/wrap-api-authentication
                       authorization
                       (fn [user]
                         (pass-groups/api-get-certs user safe (take 100 (split-commas lbls))))))

                   (sweet/GET "/envs" []
                     :return Envs
                     :query-params [safe :- String lbls :- [String]]
                     :summary "Return each environment that matches a lbl entry, max 100"
                     (auth/wrap-api-authentication
                       authorization
                       (fn [user] (pass-groups/api-get-envs user safe (take 100 (split-commas lbls))))))

                   (sweet/GET "/dbs" []
                     :return DBs
                     :query-params [safe :- String lbls :- [String]]
                     :summary "Return each db that matches a lbl entry, max 100"
                     (auth/wrap-api-authentication
                       authorization
                       (fn [user] (pass-groups/api-get-dbs user safe (take 100 (split-commas lbls))))))

                   )))

;; not authenticated wrapped
(defn provide-ss-code? [request]
  (let [params (:params request)]
    (and
      (= (utils/ensure-str (:action params)) "code")
      (:i params)
      (:k params)
      (:code params))))

(defroutes auth-routes
           (GET "/contact" request (index/view-contact request))

           (GET "/" request (index/view request))
           (GET "/pricing" request (index/view-pricing request))

           (GET "/index.html" request (index/view request))

           (GET "/securesend" request (middleware/cache-control-fn (index/securesend request)))

           (POST "/securesend" request (middleware/cache-control-fn (if (provide-ss-code? request)
                                                                      (index/securesend request)
                                                                      (index/securesend-post request))))

           (GET "/usecases/:page" [page :as request] (index/usecases request page))

           (GET "/pixel" request (pixel/view request))

           (GET "/register" request (user-routes/show-registration request))
           (POST "/register" request (user-routes/register-user request))

           (POST "/register/check-exist" request (user-routes/check-exist request))

           (GET "/login" request (user-routes/show-login request))
           (POST "/login" request (let [resp (user-routes/login-user request)]
                                       resp))

           (GET "/reset/email" request (user-routes/view-reset-login-email request))

           (GET "/reset" request (user-routes/view-reset-login request))
           (POST "/reset" request (user-routes/do-reset-login request))

           (GET "/support" request (support/view request))
           (POST "/support" request (support/create request))

           (GET "/mfa" request (user-routes/view-mfa request))
           (POST "/mfa" request (user-routes/update-mfa-login request))


           )


;; all home routes are authenticated wrapped
(defroutes home-routes

           ;;; SSE events

           (GET "/sse/pass/groups" request (pass-group-events/query-refresh-data request))

           ;;; Https requests

           ;;called just after register to fire pixels and redirect to home
           (GET "/homeregister" request (keyssrv.routes.route-utils/render "homeregister" request (assoc
                                                                                                    (:params request)
                                                                                                    :pixel-event "registered")))

           (GET "/home" request (home/view request))
           (POST "/home" request (home/wizz-update request))


           (GET "/pass/appkeys" request (appkeys/view-app-keys request))
           (POST "/pass/appkeys" request (appkeys/create-or-remove request))


           (GET "/settings" [view :as request] (settings/view-settings-items (or view "account") request))
           (POST "/settings" [view :as request] (settings/create-or-delete-settings-items view request))


           (POST "/select/pass/group" request (pass-groups/view-select-pass-group request))

           (GET "/pass/groups/:group-id" [group-id view :as request] (pass-groups/view-group-items (utils/ensure-str group-id) (or view "users") request))
           (POST "/pass/groups/:group-id" [group-id view :as request] (pass-groups/create-or-delete-group-items (utils/ensure-str group-id) view request))


           (GET "/pass/groups" request (pass-groups/view request))

           (POST "/pass/groups" request (pass-groups/create-or-delete request))


           (GET "/logout" request (user-routes/logout request)))

