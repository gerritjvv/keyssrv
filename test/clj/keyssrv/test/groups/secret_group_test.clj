(ns keyssrv.test.groups.secret-group-test
  (:require

    [keyssrv.routes.groups.secrets :as group-secrets]
    [keyssrv.test.utils :as test-utils]
    [clojure.test :refer :all]
    [keyssrv.groups.core :as groups]
    [keyssrv.utils :as utils]))


(defn setup [f]
  (test-utils/setup f))

(use-fixtures :once setup)

(defn session-with-params [user group-id m]
  (assoc
    (test-utils/session user group-id)
    :params m))

;;; Tests

(deftest test-secret-remove-create-ui
  (let [owner (test-utils/create-user)
        group (test-utils/create-group owner)
        group-id (:id group)

        lbl (test-utils/unique-str)
        secret (test-utils/unique-str)]

    (test-utils/test-no-ui-errors
      (group-secrets/add-or-remove (:gid group) (session-with-params owner group-id
                                                                     {:action "create"
                                                                      :lbl    lbl
                                                                      :secret secret})))

    (is
      (:lbl (first (group-secrets/get-group-secrets (:id group)))))

    (test-utils/test-no-ui-errors
      (group-secrets/add-or-remove (:gid group) (session-with-params owner group-id
                                                                     {:action "remove"
                                                                      :lbl    lbl})))

    (is
      (not (:lbl (first (group-secrets/get-group-secrets group-id)))))))

(deftest test-create-same-secret-in-different-groups
  (let [owner (test-utils/create-user)
        group1 (test-utils/create-group owner)
        group2 (test-utils/create-group owner)

        group-id1 (:id group1)

        group-id2 (:id group2)

        lbl (test-utils/unique-str)
        secret1 (test-utils/unique-str)
        secret2 (test-utils/unique-str)]

    (test-utils/test-no-ui-errors
      (group-secrets/add-or-remove (:gid group1) (session-with-params owner group-id1
                                                                      {:action "create"
                                                                       :lbl    lbl
                                                                       :secret secret1})))


    (test-utils/test-no-ui-errors
      (group-secrets/add-or-remove (:gid group2) (session-with-params owner group-id2
                                                                      {:action "create"
                                                                       :lbl    lbl
                                                                       :secret secret2})))))


(defn assert-secret [owner group lbl secret]
  (let [group-id (or (:id group) (:group-id group))
        record (first (filter #(= (:lbl %) lbl) (group-secrets/get-group-secrets-raw group-id)))
        db-secret (utils/ensure-str (group-secrets/decrypt-secret owner group record))]

    (is
      (= secret db-secret))))

(deftest test-secret-create-update-ui
  (let [owner (test-utils/create-user)
        group (test-utils/create-group owner)
        group-id (:id group)
        group-rel (groups/group-rel (:id owner) group-id)

        lbl (test-utils/unique-str)
        secret (test-utils/unique-str)
        lbl2 (test-utils/unique-str)
        secret2 (test-utils/unique-str)
        lbl3 (test-utils/unique-str)
        secret3 (test-utils/unique-str)
        ]

    ;; create secret
    (test-utils/test-no-ui-errors
      (group-secrets/add-or-remove (:gid group) (session-with-params owner group-id
                                                                     {:action "create"
                                                                      :lbl    lbl
                                                                      :secret secret})))


    (test-utils/test-no-ui-errors
      (group-secrets/add-or-remove (:gid group) (session-with-params owner group-id
                                                                     {:action      "update"
                                                                      :current-lbl lbl
                                                                      :new-lbl     lbl2
                                                                      :new-secret  secret2})))


    (assert-secret owner group-rel lbl2 secret2)

    ;; lbl2 is used
    (test-utils/test-no-ui-errors
      (group-secrets/add-or-remove (:gid group) (session-with-params owner group-id
                                                                     {:action      "update"
                                                                      :current-lbl lbl2
                                                                      :new-lbl     lbl3
                                                                      :new-secret  "*"})))
    (assert-secret owner group-rel lbl3 secret2)
    ;
    ;;; lbl3 is used
    (test-utils/test-no-ui-errors
      (group-secrets/add-or-remove (:gid group) (session-with-params owner group-id
                                                                     {:action      "update"
                                                                      :current-lbl lbl3
                                                                      :new-lbl     nil
                                                                      :new-secret  secret3})))
    (assert-secret owner group-rel lbl3 secret3)))
