(ns keyssrv.utils
  (:require [codex.core :as codex])
  (:import (org.apache.commons.lang3 ArrayUtils StringUtils)
           (java.util Arrays Collection Base64 UUID)
           (java.io ByteArrayOutputStream)
           (java.util.function Consumer)
           (keyssrv.util Utils)))

(defn already-exist-exception? [^Throwable e]
  (or
    (StringUtils/contains (str e) "already exists")
    (StringUtils/contains (str (.getCause e)) "already exists")))

(defn parse-str-input [v]
  (StringUtils/trimToNull (str v)))

(defn remove-nil-vals [m]
  (reduce-kv (fn [m k v]
               (if v m (dissoc m k))) m m))


(defn ensure-uuid [v]
  (if (instance? UUID v)
    v
    (UUID/fromString (StringUtils/trimToNull (Utils/toString v)))))


(defn ensure-bool [v]
  (if (string? v)
    (Boolean/parseBoolean (str v))
    (boolean v)))

(defn ensure-int [v]
  (if (string? v)
    (if-let [s (parse-str-input v)]
      (if (StringUtils/isNumeric (str s))
        (Long/parseLong s)
        0)
      0)
    (if (nil? v)
      0
      (long v))))

(defn ensure-str ^String [v]
  (cond
    (not v) nil
    (bytes? v) (String. ^"[B" v "UTF-8")
    (instance? Number v) (str v)
    (instance? UUID v) (Utils/toString v)
    (string? v) (StringUtils/trimToNull (str v))
    :else (throw (RuntimeException. (str "Type " v " not supported")))))

(defn ensure-user-name ^String [v]
  (Utils/removeWhiteSpace (StringUtils/lowerCase (ensure-str v))))

(defn ensure-user-email ^String [v]
  (Utils/removeWhiteSpace (StringUtils/lowerCase (ensure-str v))))

(defn utf-bytes ^"[B" [^String v]
  (.getBytes v "UTF-8"))


(defn encode ^"[B" [v]
  (when v
    (codex/encode (codex/kryo-encoder) v)))

(defn decode [^"[B" bts]
  (when bts
    (codex/decode (codex/kryo-encoder) bts)))

(defn ensure-bytes ^"[B" [v]
  (cond
    (nil? v) (byte-array [])
    (bytes? v) v
    (string? v) (let [v-str (StringUtils/trimToNull (str v))]
                  (if v-str
                    (utf-bytes v-str)
                    (byte-array [])))
    (instance? ByteArrayOutputStream v) (.toByteArray ^ByteArrayOutputStream v)
    :else (throw (RuntimeException. (str "Type " (type v) " not supported")))))


(defn as-coll ^Collection [v]
  (cond
    (instance? Collection v) v
    (string? v) (StringUtils/split (str v) \,)
    :else (throw (RuntimeException. (str "Type " v " not supported")))))

(defn split-bytes ^"[B" [^"[B" arr len]
  (let [cnt (count arr)]
    [(Arrays/copyOfRange arr (int 0) (int len))
     (Arrays/copyOfRange arr (int len) (int cnt))]))

(defn join-bts-array ^"[B" [^"[B" btsa ^"[B" btsb]
  (ArrayUtils/addAll btsa btsb))

(defn base64 ^String [input]
  (.encodeToString (Base64/getEncoder) ^"[B" (ensure-bytes input)))


(defn as-consumer ^Consumer [f]
  (reify Consumer
    (accept [_ t]
      (f t))))