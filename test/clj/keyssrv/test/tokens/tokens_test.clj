(ns keyssrv.test.tokens.tokens-test
  (:require [clojure.test :refer :all]
            [keyssrv.config :as config]
            [keyssrv.tokens.core :as tokens]
            [mount.core :as mount]))


(defn setup [f]
  (mount/start #'config/env)
  (mount/start #'tokens/DefaultTokenStore)

  (f))


(use-fixtures
  :once setup)


(deftest set-get-token-test
  (let [v [:a 1]
        k "ABC"
        _ (tokens/set-token k k v 10)
        v2 (tokens/get-token k k)
        _ (tokens/del-token k)
        v3 (tokens/get-token k k)]

    (is
      (= v v2))
    (is (nil? v3))))