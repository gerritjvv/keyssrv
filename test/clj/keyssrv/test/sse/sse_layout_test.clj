(ns keyssrv.test.sse.sse-layout-test
  (:require
    [keyssrv.layout :as layout]
    [clojure.test :refer :all]))


(defn test-sse-event []
  (is (=
        (:body (layout/sse-event {} nil)) ""))

  (is (= (:body (layout/sse-event {} {})) ""))

  (is (= (:body (layout/sse-event {} {:data 1})) "data:1\n\n"))


  (is (= (:body (layout/sse-event {} {:id 1 :data 2})) "id:1\ndata:2\n\n"))

  (is (= (:body (layout/sse-event {} {:id 1 :event "abc" :data 2})) "id:1\nevent:abc\ndata:2\n\n")))