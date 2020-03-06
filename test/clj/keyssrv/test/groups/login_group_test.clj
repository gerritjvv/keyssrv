(ns keyssrv.test.groups.login-group-test
  (:require

    [keyssrv.routes.groups.logins :as group-logins]
    [keyssrv.test.utils :as test-utils]
    [clojure.test :refer :all]
    [keyssrv.groups.core :as groups]
    [keyssrv.utils :as utils]
    [keyssrv.routes.groups.secrets :as group-secrets]))


(defn setup [f]
  (test-utils/setup f))

(use-fixtures :once setup)

(defn session-with-params [user group-id m]
  (assoc
    (test-utils/session user group-id)
    :params m))

(defn assert-secret [owner group user-name user-name2 login secret]
  (let [group-id (or (:id group) (:group-id group))
        _ (do (prn "records: " (group-logins/get-group-logins-raw group-id)))
        record (first (filter #(and (= (:user-name %) user-name)
                                    (= (:user-name-2 %) (or user-name2 "-"))
                                    (= (:login %) (or login "-"))) (group-logins/get-group-logins-raw group-id)))

        _ (do (prn "Record: " record " found with " login " vs " {:user-name user-name :user-name2 user-name2 :login login}))


        db-secret (when record
                    (utils/ensure-str (group-secrets/decrypt-secret owner group record)))]


    (is
      (= secret db-secret))
    record))

(defn run-group-login-insert [group-rel owner lbl user-name user-name2 login secret]
  (test-utils/test-no-ui-errors
    (group-logins/add-or-remove (:gid group-rel) (session-with-params owner (:group-id group-rel)
                                                                           {:action     "create"
                                                                            :user-name  user-name
                                                                            :user-name2 user-name2
                                                                            :lbl        lbl
                                                                            :login      login
                                                                            :secret     secret})))

  (assert-secret
    owner
    group-rel
    user-name
    user-name2
    login
    secret))

(deftest test-login-create-user-name-only-ui
  (let [owner (test-utils/create-user)
        group (test-utils/create-group owner)
        group-id (:id group)
        group-rel (assoc (groups/group-rel (:id owner) group-id)
                         :gid (:gid group))
        login (test-utils/unique-str)
        test-data [
                   [(test-utils/unique-str) (test-utils/unique-str) nil login (test-utils/unique-str)]
                   [(test-utils/unique-str) (test-utils/unique-str) (test-utils/unique-str) login (test-utils/unique-str)]
                   [(test-utils/unique-str) (test-utils/unique-str) (test-utils/unique-str) login (test-utils/unique-str)]

                   ]]

    (prn test-data)
    ;; create group login
    (doseq [[lbl
             user-name
             user-name2
             login
             secret] test-data]
      (run-group-login-insert group-rel owner lbl user-name user-name2 login secret))))


(deftest test-login-create-delete-ui
  (let [owner (test-utils/create-user)
        group (test-utils/create-group owner)
        group-id (:id group)
        group-rel (assoc
                    (groups/group-rel (:id owner) group-id)
                    :gid (:gid group))

        lbl (test-utils/unique-str)

        user-name (test-utils/unique-str)
        secret (test-utils/unique-str)
        login (str "login-" (System/nanoTime))]

    ;; create group login
    (run-group-login-insert group-rel owner lbl user-name nil login secret)

    (let [record (assert-secret
                   owner
                   group-rel
                   user-name
                   nil
                   login
                   secret)]

      (test-utils/test-no-ui-errors
        (group-logins/add-or-remove (:gid group) (session-with-params owner (:group-id group-rel)
                                                                               {:action         "remove"
                                                                                :group-login-id (:gid record)})))


      (is (empty? (group-logins/group-login-exists-by-gid? group-id (:gid record)))))))
