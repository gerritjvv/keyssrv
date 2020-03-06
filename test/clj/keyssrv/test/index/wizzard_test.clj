(ns keyssrv.test.index.wizzard-test
  (:require

    [keyssrv.routes.index.wizzards :as wizz]
    [clojure.test :refer :all]))


(deftest test-next-step
  (let [[wizz-k step-k wizz-def] (wizz/get-wizzard 1 1)]

    (is wizz-k)
    (is step-k)
    (is wizz-def)

    (is (not= step-k (wizz/step-forward wizz-def step-k (fn []))))
    (is (= step-k (wizz/step-forward wizz-def step-k (fn [] {:error "Some error"}))))
    (is (= step-k (wizz/step-forward wizz-def step-k (fn [] {:message "Some Message"}))))))

