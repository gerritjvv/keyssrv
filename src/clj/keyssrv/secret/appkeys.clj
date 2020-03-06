(ns keyssrv.secret.appkeys
    (:require
      [buddy.core.codecs :as codecs]
      [clj-uuid])
  (:import (org.apache.commons.lang3 StringUtils)
           (keyssrv.util CryptoHelper)))

(defn gen-key-id []
  (StringUtils/replace (str (clj-uuid/v4)) "-" ""))

(defn gen-key-secret []
      (codecs/bytes->hex (CryptoHelper/genKey 32)))

(defn key-secret-as-bytes ^"[B" [hex]
  (codecs/hex->bytes hex))
