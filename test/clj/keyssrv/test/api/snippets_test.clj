(ns
  ^{:doc "Get snippets from the swagger json api"}
  keyssrv.test.api.snippets-test
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

(defn get-snippets [auth-header group logins]
  (let [resp (app
               (header
                 (request :get (str "http://localhost:3001/api/v1/safes/snippets") {:safe (:name group) :lbls (clojure.string/join \, logins)})
                 "authorization"
                 auth-header))
        ]
    (update resp :body (fn [body]
                         (when body
                           (first (test-utils/body-slurp body)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;; test functions

(deftest test-bad-auth
  (utils/with-snippet-data (fn [{:keys [group snippets]}]
               (let [{:keys [status body]} (get-snippets "bla:bla" group (map :title snippets))]
                 (is (= 401 status))
                 (is (nil? body))))))

(deftest test-get-snippet
  (utils/with-snippet-data (fn [{:keys [auth-header group snippets]}]
               (let [snippet-lbls (map :title snippets)
                     resp (get-snippets auth-header group snippet-lbls)
                     json-data (:body resp)]

                 (prn resp)

                 (is (= (:status resp) 200))

                 (let [org-by-snippet (group-by :login snippets)
                       by-snippet (group-by :login json-data)]
                   (doseq [login snippet-lbls]
                     (let [record (first (get by-snippet login))
                           org-record (first (get org-by-snippet login))]

                       (is (= (select-keys org-record [:title :val])
                              (select-keys record [:title :val]))))))))))
