(ns
  ^{:doc "Get certs from the swagger json api"}
  keyssrv.test.api.certs-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :refer :all]
            [keyssrv.handler :refer :all]
            [keyssrv.test.utils :as test-utils]
            [keyssrv.test.api.utils :as utils]
            [cheshire.core :as json])
  (:import (org.apache.commons.lang3 StringUtils)))



(defn setup [f]
  (test-utils/setup f))

(use-fixtures :once setup)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;; helper functions

(defn get-certs [auth-header group & lbls]
  (let [resp (app
               (header
                 (request :get (str "http://localhost:3001/api/v1/safes/certs") {:safe (:name group) :lbls (StringUtils/join ^Iterable lbls \,)})
                 "authorization"
                 auth-header))
        ]
    (update resp :body (fn [body]
                         (when body
                           (first (test-utils/body-slurp body)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;; test functions

(deftest test-bad-auth
  (utils/with-cert-data (fn [{:keys [group lbl]}]
               (let [{:keys [status body]} (get-certs "bla:bla" group lbl)]
                 (is (= 401 status))
                 (is (nil? body))))))

(deftest test-get-cert
  (utils/with-cert-data (fn [{:keys [auth-header group lbl pub-key priv-key]}]
               (let [resp (get-certs auth-header group lbl)
                     json-data (:body resp)]

                 (is (= (:status resp) 200))

                 (is
                   (= (:lbl json-data) lbl))
                 (is
                   (= (:pub-key json-data) pub-key))
                 (is
                   (= (:priv-key json-data) priv-key))))))
