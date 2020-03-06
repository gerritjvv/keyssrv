(ns keyssrv.test.groups.snippet-group-test
  (:require

    [keyssrv.test.utils :as test-utils]
    [clojure.test :refer :all]
    [keyssrv.groups.core :as groups]
    [keyssrv.routes.groups.snippets :as group-snippets]))


(defn setup [f]
  (test-utils/setup f))

(use-fixtures :once setup)

(defn session-with-params [user group-id m]
  (assoc
    (test-utils/session user group-id)
    :params m))


(defn run-group-snippet-insert [group-rel owner title val]
  (test-utils/test-no-ui-errors
    (group-snippets/add-or-remove (:gid group-rel) (session-with-params owner (:group-id group-rel)
                                                                        {:action "create"
                                                                         :title  title
                                                                         :val    val}))))

(deftest test-snippet-create-ui
  (let [owner (test-utils/create-user)
        group (test-utils/create-group owner)
        group-id (:id group)
        group-rel (assoc
                    (groups/group-rel (:id owner) group-id)
                    :gid (:gid group))

        test-data [
                   [(test-utils/unique-str) (test-utils/unique-str)]
                   ]]

    ;; create group login
    (doseq [[title
             val] test-data]
      (run-group-snippet-insert group-rel owner title val))))


(deftest test-snippet-create-delete-ui
  (let [owner (test-utils/create-user)
        group (test-utils/create-group owner)
        group-id (:id group)
        group-rel (groups/group-rel (:id owner) group-id)

        [title val] [(test-utils/unique-str) (test-utils/unique-str)]]

    ;; create group login
    (test-utils/test-no-ui-errors
      (group-snippets/add-or-remove (:gid group) (session-with-params owner (:group-id group-rel)
                                                                      {:action "create"
                                                                       :title  title
                                                                       :val    val})))


    (let [snippet (first (group-snippets/get-group-snippets group-id))]
      (is snippet)
      (test-utils/test-no-ui-errors
        (group-snippets/add-or-remove (:gid group) (session-with-params owner (:group-id group-rel)
                                                                        {:action           "remove"
                                                                         :group-snippet-id (:gid snippet)
                                                                         :val              val})))

      (is (not (group-snippets/group-snippet-exists-by-id? (:id group) (:gid snippet)))))))
