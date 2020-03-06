(ns keyssrv.test.securesend.securesend-core-test
  (:require
    [keyssrv.securesend.core :as ss]
    [clojure.test :refer :all]))


(deftest enc-dec
  (let [v {:ts (System/currentTimeMillis) :v "message"}
        {:keys [k cipher]} (ss/-encode v)
        msg-v (ss/-decode k cipher)]

    (is
      (= v msg-v))))

(deftest enc-message-test
  (let [msg "123456"

        dread false
        expire-min 1

        a-expire-min (atom nil)
        a-id (atom nil)

        redis-fn (fn [id _ expire]
                   (reset! a-id id)
                   (reset! a-expire-min expire))

        {:keys [link k]} (ss/encrypt-message redis-fn {:msg msg :expire-min expire-min :dread dread})]


    (is
      (= link (str "?i=" @a-id "&k=" (ss/as-base64 k))))

    (is
      (= expire-min @a-expire-min))))


(deftest enc-message-redis-test
  (let [msg "123456"]
    (ss/encrypt-message-redis {:msg msg})))


