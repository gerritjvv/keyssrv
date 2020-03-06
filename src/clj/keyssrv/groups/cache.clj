(ns keyssrv.groups.cache
  (:require
    [keyssrv.groups.core :as core]
    [keyssrv.tokens.core :as tokens]
    [mount.core :as mount])
  (:import (com.google.common.cache CacheLoader CacheBuilder LoadingCache)
           (java.util.concurrent TimeUnit)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; private functions

(defn cache-key [user-id]
  (str "usg_" user-id))

(defn load-groups-from-db [user-id]
  (core/query-pass-groups-enriched {:id user-id}))

(defn load-groups-from-token-store [user-id]
  ;; we can use user-id here as session-id
  (tokens/get-token user-id (cache-key user-id)))

(defn create-group-cache []

  ;;lvl1 guava cache, 10seconds
  ;;lvl2 Redis tokens 5 minutes
  ;;lvl3 DB
  (let [load-fn (fn [user-id]
                  (if-let [data (load-groups-from-token-store user-id)]
                    data
                    (let [data (load-groups-from-db user-id)]
                      (tokens/set-token user-id (cache-key user-id) data (* 5 60))
                      data)))

        lvl1Cache (->
                    (CacheBuilder/newBuilder)
                    (.maximumSize 1000)
                    (.expireAfterWrite 10000 TimeUnit/MILLISECONDS)
                    (.build (proxy
                              [CacheLoader]
                              []
                              (load [user-id]
                                (load-fn user-id)))))]

    lvl1Cache))

(mount/defstate CACHE

                :start (create-group-cache))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; public functions

(defn invalidate [user-id]
  {:pre [user-id]}
  (.invalidate ^LoadingCache CACHE user-id)
  (tokens/del-token (cache-key user-id)))

(defn query-pass-groups-enriched-cached [user-id]
  (.get ^LoadingCache CACHE user-id))