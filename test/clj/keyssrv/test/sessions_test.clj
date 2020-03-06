(ns keyssrv.test.sessions-test
  (:require [clojure.test :refer :all]

            [keyssrv.sessions :as sessions]
            [ring.middleware.session.store :as ring-session]
            [keyssrv.test.utils :as test-utils]
            [mount.core :as mount]))


(defn setup [f]
  ;;ensure token store is always started

  (mount/start #'keyssrv.config/env #'keyssrv.tokens.core/DefaultTokenStore)
  (test-utils/setup f))


(use-fixtures
  :once setup)


(deftest set-get-session-test
  (let [store (sessions/session-store)
        _ (do (prn "store: " store))
        v {:a 1 :b [1 2 3]}
        k (ring-session/write-session store nil v)
        v2 (ring-session/read-session store k)
        _ (ring-session/delete-session store k)
        v3 (ring-session/read-session store k)]

    (prn {:store store
          :k k
          :v v :v2 v2 :v3 v3})
    (is
      (= v v2))
    (is (nil? v3))))