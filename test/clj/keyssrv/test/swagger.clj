(ns
  ^{:doc "Helper build functions to extract the swagger api and generate code for the cli/pk golang tool"}
  keyssrv.test.swagger
  (:require [ring.mock.request :refer :all]
            [keyssrv.handler :refer :all]
            [keyssrv.config :as config]
            [clj-http.client :as client]))


(defn dump-swagger-json [to-file]
  (let [resp (client/get "https://127.0.0.1:3001/swagger.json" {:accept :json :insecure? true})]
    (spit to-file (:body resp))))