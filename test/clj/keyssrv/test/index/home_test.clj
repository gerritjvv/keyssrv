(ns keyssrv.test.index.home-test
  (:require [clojure.test :refer :all]
            [keyssrv.routes.index.home :as home]))


(defn param-on [kw]
  {kw "on"})

(deftest test-get-plan-and-cycle-from-form
  (is
    (= ["basic" "year"] (home/get-plan-and-cycle-from-form (param-on :basic-year))))
  (is
    (= ["basic" "month"] (home/get-plan-and-cycle-from-form (param-on :basic-month))))
  (is
    (= ["pro" "year"] (home/get-plan-and-cycle-from-form (param-on :pro-year))))
  (is
    (= ["pro" "month"] (home/get-plan-and-cycle-from-form (param-on :pro-month))))
  (is
    (= ["free" "month"] (home/get-plan-and-cycle-from-form nil))))