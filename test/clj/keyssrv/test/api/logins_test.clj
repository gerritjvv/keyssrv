(ns
  ^{:doc "Get logins from the swagger json api"}
  keyssrv.test.api.logins-test
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

(defn get-logins [auth-header group lbls logins]
  (let [resp (app
               (header
                 (request :get (str "http://localhost:3001/api/v1/safes/logins") {:safe (:name group)
                                                                                   :logins (clojure.string/join \, logins)
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
  (utils/with-login-data (fn [{:keys [group logins]}]
               (let [{:keys [status body]} (get-logins "bla:bla" group (map :lbls logins) [])]
                 (is (= 401 status))
                 (is (nil? body))))))

(deftest test-get-login
  (utils/with-login-data (fn [{:keys [auth-header group logins]}]

               (let [login-lbls (map :lbl logins)
                     resp (get-logins auth-header group login-lbls [])
                     json-data (:body resp)]

                 (prn resp)

                 (is (= (:status resp) 200))

                 (let [org-by-login (group-by :lbl logins)
                       by-login (group-by :lbl json-data)]

                   (doseq [login login-lbls]
                     (let [record (first (get by-login login))
                           org-record (first (get org-by-login login))]

                       (is (= (select-keys org-record [:login :lbl :user-name :user-name2 :secret])
                              (select-keys record [:login :lbl :user-name :user-name2 :secret]))))))))))
