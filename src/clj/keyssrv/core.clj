(ns keyssrv.core
  (:gen-class)
  (:require [keyssrv.handler :as handler]
            [luminus.repl-server :as repl]
            [luminus-migrations.core :as migrations]
            [keyssrv.config :refer [env]]
            [compojure.handler :refer [site]]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.logging :as log]
            [org.httpkit.server :as httpkit]
            [mount.core :as mount]))


(def cli-options
  [["-p" "--port PORT" "Port number"
    :parse-fn #(Integer/parseInt %)]])


(mount/defstate ^{:on-reload :noop} http-server
  :start  (httpkit/run-server (site handler/app) {:port 3001})
  :stop  (when http-server
           (http-server :timeout 100)))

(mount/defstate ^{:on-reload :noop} repl-server
  :start
  (when (env :nrepl-port)
    (repl/start { :bind (env :nrepl-bind)
                  :port (env :nrepl-port)
                   }))
  :stop
  (when repl-server
    (repl/stop repl-server)))


(defn stop-app []
  (doseq [component (:stopped (mount/stop))]
    (log/info component "stopped"))
  (shutdown-agents))


(defn ensure-default-components-started []
  ;;Some components are not directly seen by mount on /start,
  ;; declare them here
  (mount/start #'keyssrv.sessions/DefaultSessionStore))


(defn start-app [args]
  (doseq [component (-> args
                        (parse-opts cli-options)
                        mount/start-with-args
                        :started)]

    (ensure-default-components-started)

    (log/info component "started"))
  (.addShutdownHook (Runtime/getRuntime) (Thread. ^Runnable stop-app)))

(defn run-migration [args]
  (prn "############## Doing keyssrv db migrations ##################### ")
  (migrations/migrate args (select-keys env [:database-url]))
  (prn "############## Completed migration ######################################################################## "))

(defn start-conf []
  (log/info "start server")
  (mount/start #'keyssrv.config/env))

(defn -main [& args]

  (cond
    (some #{"test"} args)
    (prn "noop")
    (some #{"init"} args)
    (do
      (start-conf)
      (migrations/init (select-keys env [:database-url :init-script]))
      (System/exit 0))
    (migrations/migration? args)
    (do
      (start-conf)
      (run-migration args)
      (System/exit 0))
    :else
    (do

      (start-conf)

      (when (nil? (:database-url env))
        (log/error ":data-url cannot be nil here")
        (System/exit 1))


      (prn "Run db migrate")
      (run-migration ["migrate"])
      (prn "Starting app")
      (start-app args))))
  
