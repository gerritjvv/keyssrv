(ns
  ^{:doc "Get dbs from the swagger json api"}
  keyssrv.test.api.dbs-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :refer :all]
            [keyssrv.handler :refer :all]
            [keyssrv.test.utils :as test-utils]
            [keyssrv.test.api.utils :as utils]
            [cheshire.core :as json]))



(defn setup [f]
  (test-utils/setup f))

(use-fixtures :once setup)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;; helper functions

(defn get-dbs [auth-header group lbls]
  (let [resp (app
               (header
                 (request :get (str "http://localhost:3001/api/v1/safes/dbs") {:safe (:name group)
                                                                                :lbls (clojure.string/join \, lbls)})
                 "authorization"
                 auth-header))
        ]
    (update resp :body (fn [body]
                         (when body
                           (test-utils/body-slurp body))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;; test functions


(deftest test-bad-auth
  (utils/with-db-data (fn [{:keys [group logins]}]
                           (let [{:keys [status body]} (get-dbs "bla:bla" group (map :lbls logins))]
                             (is (= 401 status))
                             (is (nil? body))))))

(deftest test-get-login
  (utils/with-db-data (fn [{:keys [auth-header group dbs]}]

                           (let [db-lbls (map :lbl dbs)
                                 resp (get-dbs auth-header group db-lbls)
                                 json-data (:body resp)]

                             (prn resp)
                             (prn "json-data: " json-data)

                             (is (= (:status resp) 200))

                             (let [org-by-lbl (group-by :lbl dbs)
                                   by-lbl (group-by :lbl json-data)]

                               (doseq [db db-lbls]
                                 (let [record (first (get by-lbl db))
                                       org-record (first (get org-by-lbl db))]

                                   (is (= (select-keys org-record [:type :hosted-on :lbl :val])
                                          (select-keys record [:type :hosted-on :lbl :val]))))))))))
