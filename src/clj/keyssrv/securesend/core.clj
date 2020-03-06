(ns keyssrv.securesend.core
  (:require [codex.core :as codex]
            [keyssrv.secret.keys :as keys]
            [keyssrv.tokens.core :as tokens]
            [keyssrv.utils :as utils]
            [clojure.tools.logging :as log])
  (:import (keyssrv.util CryptoHelper Utils)
           (com.google.common.io BaseEncoding)))

(defn -encoder [k]
      (codex/crypto-encoder :aes-gcm (codex/gen-expanded-key :sha128+hmac256 k) (codex/kryo-encoder)))

(defn -encode [v]
  (let [k (CryptoHelper/genKey (int 32))
        encoder (-encoder k)]

    {:k k
     :cipher (codex/encode encoder v)}))

(defn -decode [k cipher]
  (codex/decode (-encoder k) cipher))


(defn as-base64 [k]
  (.encode (BaseEncoding/base64Url) (utils/ensure-bytes k)))

(defn build-link [id k]
  (str "?i=" id "&k=" (as-base64 k)))

(defn parse-link-parts [a b]
  (when (and a b)
    {:id (utils/ensure-str a)
     :k (.decode (BaseEncoding/base64Url) (utils/ensure-str b))}))

(defn expired? [ts exp-min]
  (try
    (> (System/currentTimeMillis)
       (+ (long ts)
          (* (long exp-min) 60 1000)))
    (catch Exception e
      (log/error e)
      false)))

(defn decrypt-message
  "
  redis-get-fn [id] -> returns encrypted bytes message
  redis-del-fn [id] deletes the message at id

  Returns :msg :error :need-code

  "
  [redis-get-fn redis-del-fn {:keys [id  k code] :as rm}]
  {:pre [id k]}
  (if-let [cipher (redis-get-fn id) ]
    (let [link-parsed (parse-link-parts id k)
          {:keys [v ts exp dread] :as m} (-decode (:k link-parsed) cipher)
          m-code (utils/ensure-str (:code m))]

      (if (and m-code (not= code (:code m)))
        {:error "Code does not match" :need-code true}
        (do
          (when dread
            (redis-del-fn id))

          (if (expired? ts exp)
            {:error "Link not valid"}
            {:msg v}))))
    {:error "Link not valid"}))


(defn decrypt-message-redis
  "Returns :msg :error :need-code"
  [msg]
  {:pre [(:id msg) (:k msg)]}
  (decrypt-message (fn [id]
                     (tokens/get-raw-token id))
                   (fn [id]
                     (tokens/del-token id))
                   msg))

(defn encrypt-message
  "
   redis-fn [id cipher-msg-in-bts expire-min']

   msg : string
   expire-min long  ttl in minutes
   dread true/false if true the message is deleted on read
   returns {:link :k k}
  "
  [redis-fn {:keys [msg expire-min dread code]}]

  (let [id (keys/gen-readable-key)
        expire-min' (long (or expire-min 15))
        {:keys [k cipher]} (-encode {:ts (System/currentTimeMillis)
                                     :code (Utils/limitText (str code) (int 100))
                                     :v     (Utils/limitText (str msg) (int 500))
                                     :exp   (min expire-min' 60)
                                     :dread (boolean (or dread false))})

        link (build-link id k)]


    (redis-fn id cipher expire-min')
    {:link link :k k}))

(defn encrypt-message-redis [msg]
  {:pre [(:msg msg)]}
  (encrypt-message (fn [id v expire-min]
                     (tokens/set-raw-token id v (* (long (or expire-min 15)) 60)))
                   msg))

