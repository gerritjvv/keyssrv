(ns
  ^{:doc "

  A token store that serves for
    -> registration tokens
    -> session data keyssrv.sessions

 Security and token encryption:
   The encryption key for all session data is madeup of: secret+session-id
   The session-id itself is never stored in the token store and only the user's browser has access to this (using secure https cookies).

   To break and decrypt any particular redis data an attacker would need to know: the secret + session-id.
   The session-id expires every ttl seconds making even knowledge of the session-id and offline attacks more difficult.
   Also the session state data is never stored to disk in redis.

  "}
  keyssrv.tokens.core

  (:require [mount.core :as mount]
            [keyssrv.config :as config]
            [keyssrv.utils :as utils]
            [codex.core :as codex]
            [again.core :as again]
            [clj-uuid]
            [clojure.tools.logging :refer [info]]
            [clojure.tools.logging :as log])
  (:import (redis.clients.jedis JedisPool JedisSentinelPool Jedis)
           (org.apache.commons.lang3 StringUtils)
           (org.apache.commons.pool2.impl GenericObjectPoolConfig)
           (redis.clients.jedis.util Pool)))

;; Ensure the serializers are always registered
(codex/register-serialisers!)

(defprotocol TokenStore
  (-set-raw-token [store k v expire-seconds])
  (-get-raw-token [store k])

  (-set-token [store session-id k v expire-seconds])
  (-delete-token [store k])
  (-get-token [store session-id k]))

(defn parse-host-port [hostport]
  {:pre [(string? hostport)]}
  (let [[host port] (StringUtils/split (str hostport) \:)]
    [host (Integer/valueOf (str (or port "7379")))]))

(defn create-encoder [secret]
  (codex/crypto-encoder :aes-gcm (codex/expand-key :sha128+hmac256 secret)
                        (codex/lz4-encoder (codex/kryo-encoder))))

(defn -get-jedis-resource ^Jedis [^Pool jedisPool]
  (again/with-retries
    [500 500 500]
    (.getResource jedisPool)))

(defn -create-encoder-key [secret session-id]
  (utils/ensure-bytes
    (str (utils/ensure-str secret) (utils/ensure-str session-id))))

(defn create-jedis-token-store
  "Create a jedis backed token store.
   All operations are retried automatically to support master failover"
  [secret ^Pool jedisPool]
  {:pre [secret jedisPool]}
  ;; do connection test
  (with-open [jedis (-get-jedis-resource jedisPool)]
    (info "Got jedis connection: " jedis))

  ;;; Token store that takes any clojure value, serialize and encrypts it on storage
  ;;;  on retrieval the value is decrypted and deserialized
  (reify TokenStore

    (-get-raw-token [_ k]
      (with-open [jedis (-get-jedis-resource jedisPool)]
        (.get jedis (utils/ensure-bytes k))))

    (-set-raw-token [_ k v expire-seconds]
      (with-open [jedis (-get-jedis-resource jedisPool)]
        (let [pipe (.pipelined jedis)
              k-bts (utils/ensure-bytes k)
              resp (.set pipe k-bts ^"[B" (utils/ensure-bytes v))]

          (.expire pipe k-bts (int expire-seconds))
          (.sync pipe)
          (.get resp)                                     ;;this will trigger exceptions if any
          )))

    (-set-token [_ session-id k v expire-seconds]
      (let [encoder (create-encoder (-create-encoder-key secret session-id))]

        (with-open [jedis (-get-jedis-resource jedisPool)]
          (let [pipe (.pipelined jedis)
                k-bts (utils/ensure-bytes k)
                resp (.set pipe k-bts ^"[B" (codex/encode encoder v))]

            (.expire pipe k-bts (int expire-seconds))
            (.sync pipe)
            (.get resp)                                     ;;this will trigger exceptions if any
            ))))

    (-delete-token [_ k]
      (with-open [jedis (-get-jedis-resource jedisPool)]
        (.del jedis (utils/ensure-bytes k))))

    (-get-token [_ session-id k]
      (let [encoder (create-encoder (-create-encoder-key secret session-id))]
        (with-open [jedis (-get-jedis-resource jedisPool)]

          (when-let [data (.get jedis (utils/ensure-bytes k))]

            (codex/decode encoder data)))))))


(defn create-sentinel-jedis-pool
  "Create a JedisSentinelPool with max 100
   password can be nil"
  [{:keys [password hosts master-name]}]
  {:pre [(pos? (count hosts)) (string? master-name)]}
  (JedisSentinelPool.
    (str master-name)
    (set hosts)
    (doto
      (GenericObjectPoolConfig.)
      (.setMaxTotal (int 100)))
    (int 2000)
    ^String (when password password)))

(defn create-single-jedis-pool
  "Create a redis connection to a single instance for testing
   password can be nil"
  [{:keys [password hosts]}]
  {:pre [hosts]}
  (let [[host port] (parse-host-port (first hosts))]
    (JedisPool.
      (doto
        (GenericObjectPoolConfig.)
        (.setMaxTotal (int 100)))
      (str host) (int port)
      (int 2000)
      ^String (when password (str password)))))

(defn create-redis-token-store
  "Returns a redis backed token store"
  [{:keys [token-store]}]
  {:pre [token-store (:hosts token-store) (:secret token-store)]}
  (let [hosts (utils/as-coll (:hosts token-store))

        sentinel (:sentinel token-store)
        secret (:secret token-store)

        jedis-pool (cond
                     sentinel (create-sentinel-jedis-pool (assoc token-store :hosts hosts))
                     (= (count hosts) 1) (create-single-jedis-pool (assoc token-store :hosts hosts))
                     :else
                     (throw (RuntimeException. (str "Redis cluster support is not yet implemented"))))]


    (create-jedis-token-store secret jedis-pool)))


(mount/defstate  DefaultTokenStore
                :start (create-redis-token-store config/env)
                 :stop nil)


(defn token-store
  "Return the default global implementation of the TokenStore"
  []
  DefaultTokenStore)

(defn unique-id
  "Unique string"
  []
  (str (clj-uuid/v4)))


(defn set-raw-token
  "Set a token and returns the key k"
  [k v expire-seconds]
  (-set-raw-token (token-store) k v expire-seconds)
  k)

(defn set-token
  "Set a token and returns the key k"
  [session-id k v expire-seconds]
  (-set-token (token-store) session-id k v expire-seconds)
  k)


(defn del-token [k]
  (try
    (-delete-token (token-store) k)
    (catch Exception e
      (log/error e))))

(defn get-token
  "If any exception during parsing nil is returned and the id k is deleted"
  [session-id k]
  (try
    (-get-token (token-store) session-id k)
    (catch Exception e
      (log/error e)
      (del-token k)
      nil)))

(defn get-raw-token
  "If any exception during parsing nil is returned and the id k is deleted"
  [k]
  (try
    (-get-raw-token (token-store) k)
    (catch Exception e
      (log/error e)
      (del-token k)
      nil)))