(ns keyssrv.schemas.core
  (:require [struct.core :as st])
  (:refer-clojure :exclude [boolean byte-array map]))


;
;(defn _extract-error [v]
;  (loop [err v]
;    (if (map? err)
;      (do
;        (prn err)
;        (recur (first (vals err))))
;      err)))
;
;(defn validate [schema data]
;  (_extract-error (first (st/validate data schema))))

(defn valid? [schema data]
  (st/valid? data schema))


(defn must-be-valid!
  "Throws an exception is any of the data items does not match the schema
   otherwise return the item passed in,

   To apply to a collection of maps use apply"
  ([schema item]
   ;(prn "CHECKING " item)
   (when-let [error (first (st/validate item schema))]
     (throw (RuntimeException. (str error))))
   item)
  ([schema item & items]
   (must-be-valid! schema item)

   (doseq [item items]
     (must-be-valid! schema item))
   (conj items item)))

(defn byte-array [k]
  [k
   st/required
   {:message (str k " must be a byte array")
    :validate bytes?}])

(defn string [k]
  ;;
  [k st/string st/required
   ])

(defn number [k]
  ;; :message (str k " is required and not present")
  [k st/number st/required])

(defn decimal [k]
  [k st/required
   {:message (str k " must be a decimal or float")
    :validate #(or (decimal? %) (float? %))}])

(defn boolean [k]
  [k st/boolean st/required])

(defn record [schema k]
  (let [err (atom nil)]
    [k st/required
     {:message (str  k " invalid " (or @err ""))
      :validate #(valid? schema %)}]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;; SCHEMAS

;{:user-name user-name
; :plan      (:plan db-user-record)
; :email     (:email db-user-record)
; :id        (:id db-user-record)
; :pass-hash (:pass-hash db-user-record)
; :ts        (System/currentTimeMillis)
; :enc-key   (keys/decrypt password (:enc-key db-user-record))
; :has-mfa (when (:mfa-key-enc db-user-record) true)}

(def PLAN
  [(number :id)
   (number :type)])

(def USER
  [(number :id)
   (string :name)
   (record PLAN :plan)])


(def PRODUCT-PLAN
  [
   (number :id)
   (number :product-id)
   (string :name)
   [:type st/keyword
    {:message "Must be free,start,basic,pro"
     :validate #{:free :start :basic :pro}}]

   (decimal :cost)
   (string :curr)
   (number :max-users)
   (number :max-vaults)
   (number :max-secrets)
   (number :max-envs)
   (number :max-certs)
   (number :max-snippets)
   (number :max-logins)

   ])


(def USER-SESSION-IDENTITY
  [
   (number :id)
   (string :email)
   (byte-array :pass-hash)
   (number :ts)
   (byte-array :enc-key)
   (boolean :has-mfa)
   (record PRODUCT-PLAN :plan)
   ])
