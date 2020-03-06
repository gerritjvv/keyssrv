(ns keyssrv.test.compress-test
  (:require
    [keyssrv.compress :as c]
    [keyssrv.test.utils :as test-utils]
    [clojure.test :refer :all]
    [keyssrv.utils :as utils]))


(deftest compress-decompress-test
  (let [v (test-utils/unique-str)]

    (is
      (= v
         (utils/ensure-str (c/decompress (c/compress v)))))))