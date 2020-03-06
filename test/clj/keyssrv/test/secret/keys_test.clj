(ns keyssrv.test.secret.keys-test
  (:require [clojure.test :refer :all]
            [keyssrv.secret.keys :as keys]
            [keyssrv.test.utils :as test-utils]))


(defn setup [f]
  (test-utils/setup f))


(use-fixtures :once setup)

(deftest encrypt-decrypt
  (let [k "mykey"
        text "bla"
        encrypted (keys/encrypt k text)]

    (is
      (= text
         (String. (keys/decrypt k encrypted) "UTF-8")))))