(ns keyssrv.test.users.user-registration-test
  (:require [clojure.test :refer :all]
            [keyssrv.test.utils :as test-utils]
            [keyssrv.users.registration :as reg]
            [keyssrv.routes.users :as users]
            [clojure.tools.logging :as log]))


(defn setup [f]
  (test-utils/setup f))

(use-fixtures :once setup)

(deftest test-validate-user-login
  (log/info "-------------------------------- Expect Jargon2BackendException ---------------------------------")

  (is
    (map? (reg/validate-user-login {:pass-hash (.getBytes "bla")} {:password  "123"})))

  (log/info "-------------------------------- [End] Expect Jargon2BackendException ---------------------------------"))

(deftest test-email-validation
  (doseq [email ["samet@com.tr"
                 "yoi@example.com"
                 "you@example.com"
                 "you@gmail.com"
                 "youpro@gamil.com"
                 "riko@example.com"
                 "epirus@epirussa.gr"
                 "htmjrt@dkfrmf.com"]]
    (is
      (first (users/validate-known-bad-emails {:email email})))))

(deftest test-user-registration
  (not                                                      ;;not is ok here
    (users/validate-user-registration
      {:user-name "ABC" :email "abc@abc.com" :password "akjkjdfd$1" :password-retype "akjkjdfd$1"})))


(deftest test-user-registration-email-invalid

  (is
    (:email
      (users/validate-user-registration
        {:user-name "ABC" :email "abc" :password "akjkjdfd$1" :password-retype "akjkjdfd$1"}))))


(deftest test-user-registration-password-not-match
  (is
    (:password
      (users/validate-user-registration
        {:user-name "ABC" :email "abc@email.com" :password "akjkjdfd$1" :password-retype "bla"}))))
