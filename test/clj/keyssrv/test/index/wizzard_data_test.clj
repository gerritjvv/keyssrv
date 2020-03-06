(ns keyssrv.test.index.wizzard-data-test
  (:require [clojure.test :refer :all]
            [keyssrv.test.utils :as utils]
            [keyssrv.routes.index.wizzard-data :as wizzard-data]))

(defn setup [f]
  (utils/setup f))

(use-fixtures :once setup)

(deftest test-insert-get
  (let [user (utils/create-user)
        wizz-data {:plan :pro
                   :plan-period :year}

        _ (wizzard-data/add-wizzard-plan-data user (:plan wizz-data) (:plan-period wizz-data))

        db-wizz-data (wizzard-data/get-wizzard-plan-data user)]

    (is
      (=
        {:user-id (:id user)
         :plan (name (:plan wizz-data))
         :plan-period (name (:plan-period wizz-data))}

        (select-keys db-wizz-data [:user-id :plan :plan-period])))))