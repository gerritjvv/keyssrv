(ns keyssrv.test.settings.reset-tokens-test
  (:require

    [keyssrv.notification.notify :as notify]
    [keyssrv.routes.settings.reset-tokens :as reset-tokens]
    [keyssrv.routes.users :as users]
    [clojure.test :refer :all]
    [keyssrv.test.utils :as test-utils]
    [keyssrv.secret.keys :as keys]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; private functions

(defn extract-token-val [{:keys [url]}]
  (second (re-matches #".*token=(.*)" (str url))))

(defn session-with-params [user group-id m]
  (assoc
    (test-utils/session user group-id)
    :params m))

(deftest test-gen-keys
  (let [user (test-utils/create-user :plan-type :pro)
        {:keys [ks hash-and-vals]} (reset-tokens/gen-unique-keys user)]
    (is (not-empty ks))
    (is (not-empty hash-and-vals))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; test functions


(defn setup [f]
  (test-utils/setup f))

(use-fixtures :once setup)


(deftest validate-reset-pass-schema
  ;;validate returns nil if ok
  (is (not (users/validate-user-pass-recreate {:password "1535267909663aB1" :password-retype "1535267909663aB1"})))
  (is (users/validate-user-pass-recreate {:password "1535267909663aB1" :password-retype "bla1"}))
  (is (users/validate-user-pass-recreate {})))
;
;(defn test-reset-pass []
;  (let [user (test-utils/create-user :plan-type :pro)
;
;        ;;generate and get reset codes
;        token-id (reset-tokens/gen-reset-tokens (session-with-params user 0 {}))
;        reset-codes (reset-tokens/get-reset-tokens-by-id user token-id)
;
;        ;;start reset password using a reset code
;        _ (test-utils/test-no-ui-errors
;            (users/do-reset-login (session-with-params user 0 {:action "reset"
;                                                               :email (:email user)
;                                                               :reset-token (first reset-codes)})))
;
;        ;; get the token id that points to the reset data stored in the token store
;        reset-token-id (extract-token-val (notify/-get-reset-email notify/NOTIFIER))
;
;        ;;test view the reset password html
;        _ (users/view-reset-login (session-with-params user 0
;                                                       {:token reset-token-id}))
;
;        ;;reset password passing in the new password and the token id from the email
;        password (keys/gen-readable-key)
;
;        _ (test-utils/test-no-ui-errors
;            (users/do-reset-login (session-with-params user 0
;                                                       {:action "createpass"
;                                                        :token reset-token-id
;                                                        :password password
;                                                        :password-retype password})))
;        user' (test-utils/get-user-by-id (:id user))]
;
;    (is token-id)
;    (is (not-empty reset-codes))
;    (is reset-token-id)
;
;    ;;check that the password was changed, validate-* return nil on ok
;    (is (not (users/validate-password-hash user' password)))))