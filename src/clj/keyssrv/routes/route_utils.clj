(ns keyssrv.routes.route-utils
  (:require [ring.util.http-response :as response]
            [keyssrv.users.login :as users-login]
            [keyssrv.layout :as layout]))




(defn found-register-via-redirect-page [request message errors]

  (assoc
    (layout/render* request "redirectregister.html" nil)
    :flash {:errors  errors
            :message message}))


(defn found-same-page
  "
  If :session-append is a map its merged with the existing req session
  "
  [req message errors]
  (let [resp (assoc (response/found (str (:uri req) "?" (:query-string req)))
               :flash {:errors  errors
                       :message message})]
    resp))

(defn found303
  ([template]
   (found303 template nil nil))
  ([template message errors]
   (assoc
     (response/see-other template)
     :flash {:errors  (if (string? errors) [errors] errors)
             :message message})))

(defn found
  ([template]
   (found template nil nil))
  ([template message errors]
   (assoc
     (response/found template)
     :flash {:errors  (if (string? errors) [errors] errors)
             :message message})))

(defn render [template {:keys [flash] :as request} params]
  (let [user (users-login/user-logged-in? request)]
    (layout/render* request
                    template
                    (merge
                      {:user      user
                       :user-name (:user-name user)}
                      params
                      (select-keys flash [:name :message :errors])))))