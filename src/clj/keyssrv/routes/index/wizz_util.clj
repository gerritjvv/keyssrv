(ns keyssrv.routes.index.wizz-util
  (:require [keyssrv.users.registration :as auth-reg]
            [keyssrv.users.auth :as auth]
            [keyssrv.routes.users :as users]))


(defn update-user-wizz
  "Update the user session and db"
  [req user wizz-i step-i]
  (auth-reg/update-wizzard-db-data user wizz-i step-i)
  (users/update-user-session req
                             (auth/update-user-wizz-i
                               user
                               wizz-i
                               step-i)))