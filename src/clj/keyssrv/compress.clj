(ns
  ^{:doc "Compression for strings"}
  keyssrv.compress
  (:require [keyssrv.utils :as utils])
  (:import (com.github.luben.zstd ZstdOutputStream ZstdInputStream)
           (java.io ByteArrayOutputStream ByteArrayInputStream)
           (com.google.common.io ByteStreams)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; Public functions
(defn compress ^"[B" [v]
  (let [bts (ByteArrayOutputStream.)
        ^"[B" data-bts (utils/ensure-bytes v)]
    (doto (ZstdOutputStream. bts)
        (.write data-bts (int 0) (count data-bts))
        .close)

    (.toByteArray bts)))

(defn decompress ^"[B" [v]
  (let [input (ZstdInputStream. (ByteArrayInputStream. (utils/ensure-bytes v)))
        output (ByteArrayOutputStream.)]

    (ByteStreams/copy input output)
    (.flush output)

    (.toByteArray output)))