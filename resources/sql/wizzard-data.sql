
-- :name insert-wizzard-plan-data! :! :n
-- :doc add wizzard data
INSERT INTO wizzard_data
(user_id, plan, plan_period)
VALUES (:user-id, :plan, :plan-period)

-- :name select-wizzard-plan-data :? :*
-- :doc retrieves the user wizzard plan data
SELECT * FROM wizzard_data
WHERE user_id = :user-id;
