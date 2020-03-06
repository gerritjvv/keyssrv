(ns
  ^{:doc "Get envs from the swagger json api"}
  keyssrv.test.api.envs-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :refer :all]
            [keyssrv.handler :refer :all]
            [keyssrv.test.utils :as test-utils]
            [keyssrv.test.api.utils :as utils]))



(defn setup [f]
  (test-utils/setup f))

(use-fixtures :once setup)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;; helper functions

(defn get-envs [auth-header group lbls]
  (let [resp (app
               (header
                 (request :get (str "http://localhost:3001/api/v1/safes/envs") {:safe (:name group) :lbls (clojure.string/join \, lbls)})
                 "authorization"
                 auth-header))
        ]
    (update resp :body (fn [body]
                         (when body
                           (test-utils/body-slurp body))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;; test functions

(deftest test-bad-auth
  (utils/with-env-data (fn [{:keys [group envs]}]
               (let [{:keys [status body]} (get-envs "bla:bla" group (map :login envs))]
                 (is (= 401 status))
                 (is (nil? body))))))

(deftest test-get-envs
  (utils/with-env-data (fn [{:keys [auth-header group envs]}]
               (let [env-lbls (map :lbl envs)
                     resp (get-envs auth-header group env-lbls)
                     json-data (:body resp)]

                 (prn resp)

                 (is (= (:status resp) 200))

                 (let [org-by-lbl (group-by :login envs)
                       by-lbl (group-by :login json-data)]
                   (doseq [login env-lbls]
                     (let [record (first (get by-lbl login))
                           org-record (first (get org-by-lbl login))]

                       (is (= (select-keys org-record [:lbl :val])
                              (select-keys record [:lbl :val]))))))))))


(deftest test-get-envs-with-user
  (utils/with-env-data (fn [{:keys [group envs owner]}]
                         (let [env-lbls (map :lbl envs)
                               resp (get-envs (utils/auth-header owner) group env-lbls)]

                           (is
                             (= 200 (:status resp)))
                           (is
                             (not-empty (:body resp)))))))


(deftest test-get-envs-with-invalid-user
  (utils/with-env-data (fn [{:keys [group envs]}]
                         (let [env-lbls (map :lbl envs)
                               resp (get-envs (utils/auth-header {:user-name "bla" :password "bla"}) group env-lbls)]


                           (is
                             (= 401 (:status resp)))
                           (is
                             (empty? (:body resp)))))))
