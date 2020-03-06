(ns keyssrv.test.groups.cert-group-test
  (:require

    [keyssrv.test.utils :as test-utils]
    [clojure.test :refer :all]
    [keyssrv.groups.core :as groups]

    [keyssrv.routes.groups.certs :as group-certs]
    [keyssrv.routes.groups.users :as group-users]))


(defn setup [f]
  (test-utils/setup f))

(use-fixtures :once setup)

(defn session-with-params [user group-id m]
  (assoc
    (test-utils/session user group-id)
    :params m))


(defn run-group-cert-insert [group-rel owner lbl pub-key priv-key]
  (test-utils/test-no-ui-errors
    (group-certs/add-or-remove (:gid group-rel) (session-with-params owner (:group-id group-rel)
                                                                     {:action   "create"
                                                                      :lbl      lbl
                                                                      :pub-key  pub-key
                                                                      :priv-key priv-key}))))


(deftest test-cert-create-assigned-user-ui
  (let [owner (test-utils/create-user)
        group (test-utils/create-group owner)
        _ (groups/group-rel (:id owner) (:id group))
        user (test-utils/create-user)

        [lbl pub-key priv-key] (repeatedly 3 #(test-utils/unique-str))

        ;;create and confirm user
        req {:session {:identity (assoc owner
                                   :ts (System/currentTimeMillis))}}
        _ (group-users/add-user-to-group req (:gid group) {:user-name (:user-name user)
                                                           :admin     true})
        req2 {:session {:identity (assoc user
                                    :ts (System/currentTimeMillis))}}
        _ (group-users/confirm-user-to-group req2 (:gid group))


        _ (do                                               ;;create user-assigned-cert
            (test-utils/test-no-ui-errors
              (group-certs/add-or-remove (:gid group) (session-with-params owner (:id group)
                                                                           {:action        "create"
                                                                            :assigned-user (:user-name user)
                                                                            :lbl           lbl
                                                                            :pub-key       pub-key
                                                                            :val           priv-key}))))

        group-cert (first (group-certs/get-group-certs (:id group)))

        cert-show-resp (group-certs/add-or-remove (:gid group) (session-with-params owner (:id group)
                                                                                    {:action        "show"
                                                                                     :group-cert-id (:gid group-cert)
                                                                                     :lbl           lbl}))
        ]







    (is
      (not (zero? (count (get-in cert-show-resp [:flash :errors])))))))


(deftest test-cert-create-ui
  (let [owner (test-utils/create-user :plan-type :pro)
        group (test-utils/create-group owner)
        group-id (:id group)
        group-rel (groups/group-rel (:id owner) group-id)

        test-data [
                   (repeatedly 3 #(test-utils/unique-str))
                   ]]

    ;; create group login
    (doseq [[lbl
             pub-key
             priv-key] test-data]
      (run-group-cert-insert group-rel owner lbl pub-key priv-key))))


(deftest test-cert-create-delete-ui
  (let [owner (test-utils/create-user)
        group (test-utils/create-group owner)
        group-id (:id group)
        group-rel (groups/group-rel (:id owner) group-id)

        [lbl pub-key priv-key] (repeatedly 3 #(test-utils/unique-str))
        [new-lbl new-pub-key new-priv-key] (repeatedly 3 #(test-utils/unique-str))]

    ;; create group login
    (test-utils/test-no-ui-errors
      (group-certs/add-or-remove (:gid group) (session-with-params owner (:group-id group-rel)
                                                                   {:action  "create"
                                                                    :lbl     lbl
                                                                    :pub-key pub-key
                                                                    :val     priv-key})))


    ;;update
    (let [cert (first (group-certs/get-group-certs group-id))]
      (test-utils/test-no-ui-errors
        (group-certs/add-or-remove (:gid group) (session-with-params owner (:id group-rel)
                                                                         {:action        "update"
                                                                          :group-cert-id (:gid cert)
                                                                          :new-lbl       new-lbl
                                                                          :new-pub-key   new-pub-key
                                                                          :new-val       new-priv-key})))

      (let [certs (group-certs/get-group-certs group-id)
            cert (first certs)]
        (is cert)
        (is (= (:lbl cert) new-lbl))
        ;;:pub/priv-key are not returned with get-group-certs
        ))

    (let [cert (first (group-certs/get-group-certs group-id))]
      (is cert)
      (test-utils/test-no-ui-errors
        (group-certs/add-or-remove (:gid group) (session-with-params owner (:group-id group-rel)
                                                                         {:action        "remove"
                                                                          :group-cert-id (:gid cert)})))

      (is (not (group-certs/group-cert-exists-by-id? group-id (:gid cert)))))))
