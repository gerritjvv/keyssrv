(ns keyssrv.handler
  (:require
    [keyssrv.layout :refer [error-page]]
    [keyssrv.routes.home :refer [home-routes auth-routes api-routes]]
    [keyssrv.users.auth :as auth]
    [compojure.core :refer [routes wrap-routes]]
    [compojure.route :as route]
    [keyssrv.env :refer [defaults]]
    [mount.core :as mount]
    [keyssrv.middleware :as middleware]
    [keyssrv.sessions :as sessions]
    [keyssrv.tokens.core :as tokens]
    [ring.middleware.cors :refer [wrap-cors]])
  (:import (org.apache.commons.lang3 StringUtils)))

(mount/defstate init-app
                :start ((or (:init defaults) identity))
                :stop ((or (:stop defaults) identity)))

(defn create-api-routes []
  (routes
    #'api-routes))

(defn display-routes []
  (middleware/wrap-base
    (routes
      (-> #'auth-routes
          (wrap-routes middleware/wrap-csrf)
          (wrap-routes middleware/wrap-formats))

      (-> home-routes
          (wrap-routes auth/wrap-authentication)
          (wrap-routes middleware/wrap-csrf)
          (wrap-routes middleware/wrap-formats)
          (wrap-routes middleware/wrap-cache-control)
          (wrap-cors
            :access-control-allow-origin
            [#"https://js.stripe.com"
             #"https://stripe.com"
             #"https://api.stripe.com"
             #"https://m.stripe.com/4"
             #"https://m.stripe.network"
             #"https://stripensrq.global.ssl.fastly.net"
             #"http://localhost:3001"
             #"https://vaulthub.io"]
            :access-control-allow-methods
            [:get :put :post :delete]))
      (route/not-found
        (:body (error-page {:status 404, :title "page not found"}))))))

(defn app-routes []
  (let [api (create-api-routes)
        display (display-routes)]

    ;;return a handler that routes traffic to api routes when "/api" is used
    ;; otherwise route to normal display web app routes
    (fn [req]
      (let [uri (str (:uri req))
            api? (or
                   (StringUtils/startsWith uri "/api")
                   (StringUtils/startsWith uri "/swagger"))]
        (if api?
          (api req)
          (display req))))))

(mount/defstate app
                :start (let [_ (mount/start #'keyssrv.sessions/DefaultSessionStore)
                             app (app-routes)]

                         ;;prn components that are not automatically picked-up and we must have initialised
                         (prn "Using token store: " (tokens/token-store))
                         (prn "Using session store: " (sessions/session-store))

                         app))

