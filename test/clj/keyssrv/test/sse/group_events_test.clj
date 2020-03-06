(ns keyssrv.test.sse.group-events-test
  (:require
    [keyssrv.routes.sse.pass-group-events :as pass-group-events]
    [clojure.test :refer :all]
    [keyssrv.test.utils :as test-utils])
  (:import (org.apache.commons.lang3 StringUtils)))


(defn setup [f]
  (test-utils/setup f))

(use-fixtures :once setup)

(defn session-with-params [user group-id m]
  (assoc
    (test-utils/session user group-id)
    :params m))


(deftest test-pass-group-update-no-ts []
  (let [owner (test-utils/create-user)
        group-id (System/currentTimeMillis)

        _ (pass-group-events/notify-group-snippet-change group-id)

        body (:body (pass-group-events/query-refresh-data (session-with-params owner
                                                                               group-id
                                                                               {:gid group-id
                                                                                :v   (:snippets pass-group-events/VIEW-IDS)})))]

    (is (StringUtils/contains (str body) "event:refresh"))))