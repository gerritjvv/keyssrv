(ns
  ^{:doc "Test adding and removing users to groups"}
  keyssrv.test.groups.user-group-test
  (:require

    [keyssrv.groups.core :as groups]
    [keyssrv.routes.pass-groups :as group-ui]
    [keyssrv.routes.groups.users :as group-users]
    [keyssrv.test.utils :as test-utils]
    [clojure.test :refer :all])
  (:import (java.util UUID)))

(defn setup [f]
  (test-utils/setup f))

(use-fixtures :once #'setup)

(deftest test-group-add-user
  (let [owner (test-utils/create-user)
        group (test-utils/create-group owner)
        group-rel (groups/group-rel (:id owner) (:id group))
        user (test-utils/create-user)

        record (group-users/do-share-request owner group-rel {:user-id (:id user) :admin true})]

    (is record)
    (is (false? (:confirmed record)))))


(deftest test-remove-user-not-exist-from-group-ui
  (let [owner (test-utils/create-user)
        group (test-utils/create-group owner)

        req {:test-utils/session {:identity (assoc owner
                                              :ts (System/currentTimeMillis))}}

        resp (group-users/remove-user-from-group req (:gid group) {:user-id (UUID/randomUUID)})]

    (is
      (:status resp) 302)
    (is
      (not-empty (get-in resp [:flash :errors])))))


(deftest test-remove-user-owner-from-group-ui
  (let [owner (test-utils/create-user)
        group (test-utils/create-group owner)

        req {:session {:identity (assoc owner
                                   :ts (System/currentTimeMillis))}}

        resp (group-users/remove-user-from-group req (:gid group) {:user-id (:gid owner)})]

    (is
      (:status resp) 302)))

(deftest test-remove-user-to-group-ui
  (let [owner (test-utils/create-user)
        group (test-utils/create-group owner)
        user (test-utils/create-user)

        req {:session {:identity (assoc owner
                                   :ts (System/currentTimeMillis))}}
        _ (group-users/add-user-to-group req (:gid group) {:user-name (:user-name user)
                                                           :admin     true})

        resp (group-users/remove-user-from-group req (:gid group) {:user-id (:gid user)})]

    (is
      (:status resp) 302)
    (is
      (nil? (get-in resp [:flash :errors])))))


(deftest test-add-user-to-group-ui
  (let [owner (test-utils/create-user)
        group (test-utils/create-group owner)
        _ (groups/group-rel (:id owner) (:id group))
        user (test-utils/create-user)

        req {:session {:identity (assoc owner
                                   :ts (System/currentTimeMillis))}}
        resp (group-users/add-user-to-group req (:gid group) {:user-name (:user-name user)
                                                              :admin     true})]

    (is
      (:status resp) 302)
    (is
      (nil? (get-in resp [:flash :errors])))))

(deftest test-add-user-not-exist-to-group-ui

  (let [owner (test-utils/create-user)
        group (test-utils/create-group owner)
        _ (groups/group-rel (:id owner) (:id group))

        _ (do (prn ">>>>> test-add-user-not-exist-to-group-ui >>>> " group)
              )

        req {:session {:identity (assoc owner
                                   :ts (System/currentTimeMillis))}}
        resp (group-users/add-user-to-group req (:gid group) {:user-name (str (System/currentTimeMillis))
                                                              :admin     true})]

    (is
      (:status resp) 302)
    (is
      (not-empty (get-in resp [:flash :errors])))))


(deftest test-add-user-owner-to-group-ui
  (let [owner (test-utils/create-user)
        group (test-utils/create-group owner)
        _ (groups/group-rel (:id owner) (:id group))

        user (test-utils/create-user)

        req {:session {:identity (assoc owner
                                   :ts (System/currentTimeMillis))}}
        resp (group-users/add-user-to-group req (:gid group) {:user-name (:user-name user)
                                                              :admin     true})
        resp2 (group-users/add-user-to-group req (:gid group) {:user-name (:user-name user)
                                                               :admin     true})]

    (is
      (:status resp) 302)
    (is
      (:status resp2) 302)


    (is
      (nil? (get-in resp [:flash :errors])))
    ;;expect an error
    (is
      (not-empty (get-in resp2 [:flash :errors])))))

(deftest test-add-user-twice-to-group-ui
  (let [owner (test-utils/create-user)
        group (test-utils/create-group owner)
        _ (groups/group-rel (:id owner) (:id group))

        req {:session {:identity (assoc owner
                                   :ts (System/currentTimeMillis))}}
        resp (group-users/add-user-to-group req (:gid group) {:user-name (:user-name owner)
                                                              :admin     true})]

    (is
      (:status resp) 302)

    ;;expect an error
    (is
      (not-empty (get-in resp [:flash :errors])))))


(deftest test-add-confirm-user-to-group-ui
  (let [owner (test-utils/create-user)
        group (test-utils/create-group owner)
        _ (groups/group-rel (:id owner) (:id group))
        user (test-utils/create-user)

        req {:session {:identity (assoc owner
                                   :ts (System/currentTimeMillis))}}

        resp (group-users/add-user-to-group req (:gid group) {:user-name (:user-name user)
                                                             :admin     true})

        req2 {:session {:identity (assoc user
                                    :ts (System/currentTimeMillis))}}

        resp2 (group-users/confirm-user-to-group req2 (:id group))]

    (is
      (:status resp) 302)
    (is
      (nil? (get-in resp [:flash :errors])))
    (is
      (:status resp2) 302)
    (is
      (nil? (get-in resp2 [:flash :errors])))))

(deftest test-add-confirm-user-to-group-ui
  (let [owner (test-utils/create-user)
        group (test-utils/create-group owner)
        _ (groups/group-rel (:id owner) (:id group))
        user (test-utils/create-user)

        req {:session {:identity (assoc owner
                                   :ts (System/currentTimeMillis))}}

        resp (group-users/add-user-to-group req (:gid group) {:user-name (:user-name user)
                                                             :admin     true})

        req2 {:session {:identity (assoc user
                                    :ts (System/currentTimeMillis))}}

        resp2 (group-users/confirm-user-to-group req2 (:gid group))]

    (is
      (:status resp) 302)
    (is
      (nil? (get-in resp [:flash :errors])))
    (is
      (:status resp2) 302)
    (is
      (nil? (get-in resp2 [:flash :errors])))))

(deftest test-user-group-delete-with-rel-ui
  (let [owner (test-utils/create-user)
        group (test-utils/create-group owner)
        group-id (:id group)

        group-rel (groups/group-rel (:id owner) group-id)
        user (test-utils/create-user)

        _ (group-users/do-share-request owner group-rel {:user-id (:id user) :admin true})

        group-rel (groups/group-rel (:id user) group-id)]

    (is (not-empty group-rel))

    ;;remove non owner
    (test-utils/test-no-ui-errors (group-ui/delete (test-utils/session user (:gid group))))


    ;;;check that the group rel was removed
    (test-utils/test-no-ui-errors (groups/group-rel (:id user) group-id))

    ;;;check the group still exists for owner
    (is (not-empty (groups/pass-group-exists-by-id? (:id owner) (:gid group))))

    ;;;delete group for owner
    (test-utils/test-no-ui-errors (group-ui/delete (test-utils/session owner (:gid group))))

    ;;;check that the group does not exist
    (is (empty? (groups/pass-group-exists-by-id? (:id owner) (:gid group))))))