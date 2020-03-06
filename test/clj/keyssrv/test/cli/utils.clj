(ns keyssrv.test.cli.utils
  (:require [clojure.test :refer :all]
            [keyssrv.test.pkcli :as pkcli]
            [keyssrv.test.utils :as utils]))


(defn setup [f]
  (utils/setup #())
  (pkcli/build)
  (f))

