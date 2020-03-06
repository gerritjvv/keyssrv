(ns keyssrv.test.securesend.securesend-int-test
  (:require
    [keyssrv.securesend.index :as ss-i]

    [clojure.test :refer :all]
    [keyssrv.test.utils :as test-utils])
  (:import (org.eclipse.jetty.util MultiMap UrlEncoded)))



(defn setup [f]
  (test-utils/setup f))

(use-fixtures :once setup)


(defn parse-query-string [query]
  (let [params (MultiMap.)]
    (UrlEncoded/decodeTo query params "UTF-8")
    (into {} params)))


(defn parse-link-parts [link]
  (let [query (parse-query-string (last (clojure.string/split link #"\?")))]
    {:i (first (get query "i"))
     :k (first (get query "k"))}))

(defn encrypt [msg dread expire-min]
  (ss-i/encrypt {:params {:secret (str msg) :dread (str dread) :expire (str expire-min)}}))


(defn decrypt [link]
  (let [{:keys [i k]} (parse-link-parts link)]
    (ss-i/descrypt {:params {:i i :k k}})))

(defn check-error! [resp]
  (when (:error resp)
    (throw (RuntimeException. (str (:error resp)))))

  resp)

(defn encrypt-decrypt [msg dread expire-min test-fn]
  (let [{:keys [link]} (check-error! (encrypt msg dread expire-min))

        res1 (decrypt link)
        res2 (decrypt link)]

    (test-fn msg res1 res2)))


(defn test-encrypt-decrypt-params []
  (let [tests [[{:dread "true" :expire-min 15 :msg "bla" }
                (fn [msg res1 res2]
                  (is (= msg "bla"))
                  (is (= msg (:msg res1)))
                  (is (empty? (:msg res2)))
                  (is (:error res2)))]]]

    (doseq [[data f] tests]
      (encrypt-decrypt (:msg data) (:dread data) (:expire-min data) f))))