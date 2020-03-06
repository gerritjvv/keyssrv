(ns keyssrv.test.config-test
  (:require [clojure.test :refer :all]
            [keyssrv.config :as c]))


(deftest test-config-env
  (is
    (=
      (c/swap-as-per-environment :dev {:dev-db-url 123})
      {:dev-db-url 123 :db-url 123})))