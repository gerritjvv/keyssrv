(ns keyssrv.routes.pixel
  (:require [keyssrv.routes.user-events :as user-events]
            [keyssrv.users.auth :as auth]
            [ring.util.response :as response]))

(defn view
  "Pixel event, note the request might or not be authenticated
  The pixel call is embedded in each page with its own event.
  We serve a static 1x1 page
  "
  [{:keys [params] :as req}]
  (let [{:keys[event uri]} params
        user (auth/user-logged-in? req)]

    (when user
      (user-events/user-visit! user event (or uri (:uri params))))

    (response/file-response "resources/public/img/pixel.gif")))
