(ns keyssrv.test.appkeys.keys-test
  (:require
    [keyssrv.routes.appkeys :as appkeys]
    [keyssrv.test.utils :as test-utils]
    [clj-time.format :as f]
    [clj-time.core :as t]
    [clojure.test :refer :all]
    [keyssrv.users.auth :as auth]
    [keyssrv.secret.appkeys :as akeys]
    [keyssrv.secret.keys :as keys]))


(defn setup [f]
  (test-utils/setup f))

(use-fixtures :once setup)

(defn session-with-params [user m]
  (assoc
    (test-utils/session user -1)
    :params m))



(deftest test-create-remove-key
  (let [user (test-utils/create-user)
        _ (test-utils/test-no-ui-errors
            (appkeys/create-or-remove (session-with-params user {:action     "create"
                                                                 :dateexpire (f/unparse appkeys/DATE-FORMATTER (t/from-now (t/years 1)))})))
        app-key (first (appkeys/get-user-app-keys user))]


    (prn app-key)
    (is (seq app-key))

    (test-utils/test-no-ui-errors
      (appkeys/create-or-remove (session-with-params user {:action "remove"
                                                           :id     (:id app-key)})))

    (is (nil? (seq (first (appkeys/get-user-app-keys user)))))))



(deftest test-encryt-decrypt-with-key
  (let [key-secret (akeys/gen-key-secret)
        text "bla"
        cipher-text (keys/encrypt (akeys/key-secret-as-bytes key-secret) text)
        ]

    (is
      (= text (String. (keys/decrypt (akeys/key-secret-as-bytes key-secret) cipher-text))))))

(deftest test-create-auth-key-decrypt
  (let [user (test-utils/create-user)

        {:keys [key-secret enc-key-enc]} (appkeys/create-user-app-key user nil)]

    (keys/decrypt (akeys/key-secret-as-bytes key-secret) enc-key-enc)



    ))

(deftest test-create-auth-key-decrypt2

    (let [user (test-utils/create-user)

          {:keys [key-id key-secret]} (appkeys/create-user-app-key user nil)

          _ (do
              (is key-id)
              (is key-secret))
          user-a (atom nil)
          handler (fn [user] (reset! user-a user))
          _ (auth/wrap-key-authentication (str key-id ":" key-secret) handler)

          ]



      (is (not-empty @user-a))

      ))