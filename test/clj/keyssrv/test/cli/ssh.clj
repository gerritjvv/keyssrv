(ns keyssrv.test.cli.ssh
  (:require [clojure.test :refer :all])
  (:import (org.apache.sshd.server SshServer)
           (org.apache.sshd.server.keyprovider SimpleGeneratorHostKeyProvider)
           (org.apache.sshd.server.shell ProcessShellFactory)
           (java.nio.file Path Paths)
           (org.apache.sshd.server.auth.password PasswordAuthenticator)))

;
;(defn start-sshd [authenticator-f]
;  (let [port (int 28)
;        server (authenticator-f
;                 (doto
;                   (SshServer/setUpDefaultServer)
;                   (.setPort (int port))
;                   (.setKeyPairProvider
;                     (SimpleGeneratorHostKeyProvider. (Paths/get "/tmp" ^"[Ljava.lang.String;" (into-array ["/tmp/keyssrv-sshd.cer"]))))
;                   (.setShellFactory
;                     (ProcessShellFactory. ^"[Ljava.lang.String;" (into-array ["/bin/sh", "-i", "-l"])))
;                   ;(.setCommandFactory (ScpCommandFactory.))
;                   .start))]
;
;    {:server server
;     :port port}))
;
;(defn start-ssh-passwordauth ^SshServer [user-name password]
;  (start-sshd (fn [^SshServer server]
;                (.setPasswordAuthenticator
;                  (reify PasswordAuthenticator
;                    (authenticate [_ user-name' password' _]
;                      (and
;                        (= user-name user-name')
;                        (= password password'))))))))
;
;(defn stop-sshd [{:keys [server]}]
;  (when server
;    (.stop ^SshServer server true)))