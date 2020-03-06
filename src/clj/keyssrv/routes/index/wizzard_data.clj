(ns keyssrv.routes.index.wizzard-data
  (:require [keyssrv.db.core :as db]))


(defn add-wizzard-plan-data [{:keys [id]} plan plan-period]
  {:pre [(number? id) (keyword? plan) (keyword? plan-period) (#{:year :month} plan-period)]}
  (db/insert-wizzard-plan-data! {:user-id id
                                :plan (name plan)
                                :plan-period (name plan-period)}))


(defn get-wizzard-plan-data [{:keys [id]}]
  {:pre [(number? id)]}
  (first (db/select-wizzard-plan-data {:user-id id})))
