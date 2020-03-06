(ns keyssrv.securesend.index
  (:require [keyssrv.securesend.core :as ss]
            [keyssrv.utils :as utils]))



(defn encrypt [{:keys [params]}]
  (let [msg (utils/ensure-str (:secret params))
        dread (utils/ensure-bool (:dread params))
        code (utils/ensure-str (:code params))
        expire-min (utils/ensure-int (:expire params))

        {:keys [link]} (ss/encrypt-message-redis {:msg        msg
                                                  :dread      dread
                                                  :code       code
                                                  :expire-min expire-min})]

    {:link (str "securesend" link)}))

(defn descrypt [{:keys [params] :as v}]
  (let [id (utils/ensure-str (:i params))
        k (utils/ensure-str (:k params))
        code (utils/ensure-str (:code params))

        {:keys [msg error need-code]} (if (and id k)
                                        (ss/decrypt-message-redis {:id id :k k :code code})
                                        {:error "Link is not valid, no id or k params"})]


    (let [ret {:msg (str msg) :error error :need-code need-code}]
      (if need-code
        (assoc ret :i id :k k)
        ret))))

(defn securesend-read [request]
  (descrypt request))

(defn securesend [{:keys [params] :as request}]
  (let [action (:action params)]

    (cond
      (= action "encrypt") (encrypt request)
      (= action "code") (descrypt request)


      :else
      (throw (RuntimeException. (str "Action not supported " action))))))

