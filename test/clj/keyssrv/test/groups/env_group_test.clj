(ns keyssrv.test.groups.env-group-test
  (:require

    [keyssrv.test.utils :as test-utils]
    [clojure.test :refer :all]
    [keyssrv.groups.core :as groups]
    [keyssrv.routes.groups.envs :as group-envs]))


(defn setup [f]
  (test-utils/setup f))

(use-fixtures :once setup)

(defn session-with-params [user group-id m]
  (assoc
    (test-utils/session user group-id)
    :params m))


(defn run-group-env-insert [group-id group-rel owner lbl val]
  (test-utils/test-no-ui-errors
    (group-envs/add-or-remove group-id (session-with-params owner (:group-id group-rel)
                                                            {:action "create"
                                                             :lbl    lbl
                                                             :val    val}))))

(deftest test-env-create-ui
  (let [owner (test-utils/create-user)
        group (test-utils/create-group owner)
        group-id (:id group)
        group-rel (groups/group-rel (:id owner) group-id)

        test-data [
                   (repeatedly 2 #(test-utils/unique-str))
                   ]]

    ;; create group login
    (doseq [[lbl
             val] test-data]
      (run-group-env-insert (:gid group) group-rel owner lbl val))))


(deftest test-env-create-delete-ui
  (let [owner (test-utils/create-user)
        group (test-utils/create-group owner)
        group-id (:id group)
        group-rel (groups/group-rel (:id owner) group-id)

        [lbl val] (repeatedly 2 #(test-utils/unique-str))
        [new-lbl new-val] (repeatedly 2 #(test-utils/unique-str))]

    ;; create group login
    (test-utils/test-no-ui-errors
      (group-envs/add-or-remove (:gid group) (session-with-params owner (:group-id group-rel)
                                                                  {:action "create"
                                                                   :lbl    lbl
                                                                   :val    val})))


    ;;update
    (let [env (first (group-envs/get-group-envs group-id))]
      (test-utils/test-no-ui-errors
        (group-envs/add-or-remove (:gid group) (session-with-params owner (:group-id group-rel)
                                                                    {:action       "update"
                                                                     :group-env-id (:gid env)
                                                                     :new-lbl      new-lbl
                                                                     :new-val      new-val})))

      (let [env (first (group-envs/get-group-envs group-id))]

        (is env)
        (is (= (:lbl env) new-lbl))))

    (let [env (first (group-envs/get-group-envs group-id))]
      (is env)
      (test-utils/test-no-ui-errors
        (group-envs/add-or-remove (:gid group) (session-with-params owner (:group-id group-rel)
                                                                    {:action       "remove"
                                                                     :group-env-id (:gid env)})))

      (is (not (group-envs/group-env-exists-by-id? (:id group) (:gid env)))))))
