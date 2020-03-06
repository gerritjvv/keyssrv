-- :name create-user! :i!
-- :doc creates a new user record, mfa_key_enc is for totp or other multi factor keys, the mfa is encrypted with the users password
INSERT INTO users
(user_name, email, pass_hash, mfa_key_enc, confirmed, org, enc_key, wiz, wiz_step, pass_info)
VALUES (:user-name, :email, :pass-hash, :mfa-key-enc, :confirmed, :org, :enc-key, :wiz, :wiz-step, :pass-info)


-- :name update-user-email-confirmed! :! :n
-- :doc update user confirmed
UPDATE users
SET confirmed = :confirmed
WHERE id = :id

-- :name update-user-pass-enc! :! :n
-- :doc update user pass enc
UPDATE users
SET pass_hash = :pass-hash, enc_key = :enc-key, pass_info = :pass-info
WHERE id = :id

-- :name update-user-pass-hash! :! :n
-- :doc update user pass enc
UPDATE users
SET pass_hash = :pass-hash, pass_info = :pass-info
WHERE id = :id

-- :name update-user-mfa-enc! :! :n
-- :doc update user mfa code
UPDATE users
SET mfa_key_enc = :mfa-key-enc
WHERE id = :id


-- :name delete-user-mfa! :! :n
-- :doc update user mfa code
UPDATE users
SET mfa_key_enc = null
WHERE id = :id

-- :name update-user-wizz! :! :n
-- :doc update user mfa code
UPDATE users
SET wiz = :wiz, wiz_step = :wiz-step
WHERE id = :id

-- :name get-users :? :n
-- :doc retrieves all users
SELECT * FROM users

-- :name get-user-by-email :? :1
-- :doc retrieves a user record given the email
SELECT * FROM users
WHERE lower(email) = lower(:email)

-- :name get-user-by-user-name :? :1
-- :doc retrieves a user record given the user name
SELECT * FROM users
WHERE lower(user_name) = lower(:user-name)

-- :name get-user-by-id :? :1
-- :doc retrieves a user record given the id
SELECT * FROM users
WHERE id = :id

-- :name get-user-by-gid :? :1
-- :doc retrieves a user record given the gid
SELECT * FROM users
WHERE gid = :gid

-- :name delete-user! :! :n
-- :doc deletes a user record given the id
DELETE FROM users
WHERE id = :id

-- :name create-password-group! :i!
-- :doc creates a new user password group and return the record id, see https://www.hugsql.org/#using-insert for generated keys
INSERT INTO password_groups
(user_id, name)
VALUES (:user-id, :name)

-- :name select-user-password-groups :? :*
-- :doc returns all the groups that the user has created or has been shared with the user
SELECT * FROM password_groups g
LEFT JOIN password_groups_users pgu on g.id = pgu.group_id
WHERE pgu.user_id = :user-id
ORDER BY g.name

-- :name delete-user-group! :! :n
-- :doc deletes a user record given the id
DELETE FROM password_groups
WHERE user_id = :user-id AND id = :group-id


-- :name delete-user-from-group! :! :n
-- :doc removes a user from a group rel
DELETE FROM password_groups_users
WHERE user_id = :user-id AND group_id = :group-id

-- :name create-password-group-user! :! :n
-- :doc creates a new user password group
INSERT INTO password_groups_users
(owner, user_id, group_id, user_group_master_key_enc, is_admin, confirmed)
VALUES (:owner, :user-id, :group-id, :user-group-master-key-enc, :is-admin, :confirmed)

-- :name confirm-user-group-share! :! :n
-- :doc confirm a user group
UPDATE password_groups_users gu
SET user_group_master_key_enc = :user-group-master-key-enc,
    confirmed = true
WHERE gu.user_id = :user-id AND gu.group_id = :group-id

-- :name select-password-group-shared-users :? :*
-- :doc retrieves users (joined in from the users table) the password group has been shared with
select * from password_groups_users gu , users u
WHERE gu.group_id = :group-id AND gu.user_id = u.id

-- :name select-total-share-user-count :? :1
-- :doc total count of users that the owner has shared groups with
select count(*) from password_groups_users
WHERE owner = :user-id and owner != user_id

-- :name select-total-user-group-count :? :1
-- :doc total count of user groups
select count(*) from password_groups
WHERE user_id = :user-id

-----------

-- :name select-total-user-secrets :? :1
-- :doc total count of user secrets over all groups
select count(*) from password_groups_secrets pgs
JOIN public.password_groups pg on pgs.group_id = pg.id
and pg.user_id = :user-id;

-- :name select-total-user-logins :? :1
-- :doc total count of user logins over all groups
select count(*) from password_groups_logins pgs
JOIN public.password_groups pg on pgs.group_id = pg.id
and pg.user_id = :user-id;

-- :name select-total-user-dbs :? :1
-- :doc total count of user dbs over all groups
select count(*) from password_groups_dbs pgs
JOIN public.password_groups pg on pgs.group_id = pg.id
and pg.user_id = :user-id;

-- :name select-total-user-certs :? :1
-- :doc total count of user certs over all groups
select count(*) from password_groups_certs pgs
JOIN public.password_groups pg on pgs.group_id = pg.id
and pg.user_id = :user-id;

-- :name select-total-user-snippets :? :1
-- :doc total count of user snippets over all groups
select count(*) from password_groups_snippets pgs
JOIN public.password_groups pg on pgs.group_id = pg.id
and pg.user_id = :user-id;

-- :name select-total-user-envs :? :1
-- :doc total count of user envs over all groups
select count(*) from password_groups_envs pgs
JOIN public.password_groups pg on pgs.group_id = pg.id
and pg.user_id = :user-id;

-- :name select-password-group-rel-shared-with-user :? :1
-- :doc retrieves the password group relation for a group shared with a user
select * from password_groups_users gu
WHERE gu.group_id = :group-id AND gu.user_id = :user-id


-- :name get-user-password-group-by-name :? :1
-- :doc retrieves a user record given the id
SELECT
g.id,
g.name,
pgu.owner,
pgu.group_id,
pgu.user_id,
pgu.user_group_master_key_enc,
pgu.is_admin,
pgu.confirmed
FROM password_groups g
LEFT JOIN password_groups_users pgu on g.id = pgu.group_id
WHERE pgu.user_id = :user-id AND g.name = :name

-- :name get-user-password-group-by-id :? :1
-- :doc retrieves a user record given the id
SELECT
g.id,
g.gid,
g.name,
pgu.owner,
pgu.group_id,
pgu.user_id,
pgu.user_group_master_key_enc,
pgu.is_admin,
pgu.confirmed
FROM password_groups g
LEFT JOIN password_groups_users pgu on g.id = pgu.group_id
WHERE pgu.user_id = :user-id AND g.gid = :group-id



-- :name select-secrets :? :*
-- :doc retrieves the secrets assigned to a group
SELECT * FROM password_groups_secrets
WHERE group_id = :group-id
ORDER BY lbl

-- :name get-secrets-by-lbls :? :*
-- :doc retrieves a single secret by group and lbl
SELECT * FROM password_groups_secrets
WHERE group_id = :group-id
AND lbl IN (:v*:lbls)
ORDER BY lbl

-- :name get-secret-by-lbl :? :1
-- :doc retrieves a single secret by group and lbl
SELECT * FROM password_groups_secrets
WHERE group_id = :group-id
AND lbl = :lbl
ORDER BY lbl

-- :name insert-secret! :i!
-- :doc insert a new password_groups_secrets
INSERT INTO password_groups_secrets
(group_id, lbl, val_enc)
VALUES (:group-id, :lbl, :val-enc)

-- :name delete-secret! :! :n
-- :doc delete a secret for a group
DELETE FROM password_groups_secrets
WHERE group_id = :group-id AND lbl = :lbl

-- :name update-secret-lbl-and-val! :! :n
-- :doc update the label and secret in password_groups_secrets
UPDATE password_groups_secrets
SET lbl = :new-lbl, val_enc = :val-enc
WHERE group_id = :group-id AND lbl = :lbl

-- :name update-secret-lbl! :! :n
-- :doc update the label in password_groups_secrets
UPDATE password_groups_secrets
SET lbl = :new-lbl
WHERE group_id = :group-id AND lbl = :lbl

-- :name update-secret-val! :! :n
-- :doc update the label in password_groups_secrets
UPDATE password_groups_secrets
SET val_enc = :val-enc
WHERE group_id = :group-id AND lbl = :lbl






-- :name select-group-logins :? :*
-- :doc retrieves the secrets assigned to a group
SELECT * FROM password_groups_logins
WHERE group_id = :group-id
ORDER BY login, user_name, user_name2

-- :name select-group-dbs :? :*
-- :doc retrieves the secrets assigned to a group
SELECT * FROM password_groups_dbs
WHERE group_id = :group-id
ORDER BY lbl

-- :name get-group-login-by-user-names :? :1
-- :doc retrieves a single group login by usernames and logins
SELECT * FROM password_groups_logins
WHERE group_id = :group-id AND
lbl = :lbl AND
user_name = :user-name AND
user_name2 = :user-name2
ORDER BY login,user_name,user_name2

-- :name get-group-db-by-lbl :? :1
-- :doc retrieves a single group dbs by lbl
SELECT * FROM password_groups_dbs
WHERE group_id = :group-id AND
lbl = :lbl
ORDER BY lbl

-- :name get-group-db-by-lbls :? :*
-- :doc retrieves all dbs for the lbls provided
SELECT * FROM password_groups_dbs
WHERE group_id = :group-id
AND lbl IN (:v*:lbls)
ORDER BY lbl

-- :name get-group-login-by-id :? :1
-- :doc retrieves a single group login by its id
SELECT * FROM password_groups_logins
WHERE group_id = :group-id AND id = :id

-- :name get-group-login-by-gid :? :1
-- :doc retrieves a single group login by its id
SELECT * FROM password_groups_logins
WHERE group_id = :group-id AND gid = :gid

-- :name get-group-db-by-id :? :1
-- :doc retrieves a single group db by its id
SELECT * FROM password_groups_dbs
WHERE group_id = :group-id AND id = :id

-- :name get-group-db-by-gid :? :1
-- :doc retrieves a single group db by its id
SELECT * FROM password_groups_dbs
WHERE group_id = :group-id AND gid = :gid

-- :name insert-group-login! :i!
-- :doc insert a new password_groups_logins
INSERT INTO password_groups_logins
(group_id, lbl, user_name, user_name2, login, val_enc)
VALUES (:group-id, :lbl, :user-name, :user-name2, :login, :val-enc)

-- :name insert-group-db! :i!
-- :doc insert a new password_groups_dbs
INSERT INTO password_groups_dbs
(group_id, lbl, type, hosted_on, val_enc)
VALUES (:group-id, :lbl, :type, :hosted-on, :val-enc)

-- :name delete-group-login-by-id! :! :n
-- :doc removes a single group login by its id
DELETE FROM password_groups_logins
WHERE group_id = :group-id AND id = :id

-- :name delete-group-db-by-id! :! :n
-- :doc removes a single group db by its id
DELETE FROM password_groups_dbs
WHERE group_id = :group-id AND id = :id

-- :name update-group-login! :! :n
-- :doc update all but the val-enc
UPDATE password_groups_logins
SET
lbl = :lbl,
user_name = :user-name,
user_name2 = :user-name2,
login = :login
WHERE group_id = :group-id AND id = :id

-- :name update-group-login-and-secret! :! :n
-- :doc update all including the secret
UPDATE password_groups_logins
SET
lbl = :lbl,
user_name = :user-name,
user_name2 = :user-name2,
login = :login,
val_enc = :val-enc
WHERE group_id = :group-id AND id = :id

-- :name update-group-db! :! :n
-- :doc update all including the secret
UPDATE password_groups_dbs
SET
lbl = :lbl,
type = :type,
hosted_on = :hosted-on,
val_enc = :val-enc
WHERE group_id = :group-id AND id = :id

-- :name select-group-snippets-titles :? :*
-- :doc retrieves titles and ids from the password_groups_snippets
SELECT id, gid, title FROM password_groups_snippets
WHERE group_id = :group-id
ORDER BY title


-- :name get-group-snippet-by-title :? :*
-- :doc retrieves all snippets for the titles provides
SELECT * FROM password_groups_snippets
WHERE group_id = :group-id
AND title IN (:v*:titles)
ORDER BY title


-- :name get-group-snippet-by-gid :? :1
-- :doc retrieves a single group snippet by its id
SELECT * FROM password_groups_snippets
WHERE group_id = :group-id AND gid = :gid

-- :name get-group-snippet-title-by-gid :? :1
-- :doc retrieves a single group snippet by its id
SELECT id, gid, title FROM password_groups_snippets
WHERE group_id = :group-id AND gid = :gid

-- :name insert-group-snippet! :i!
-- :doc insert a new password_groups_snippets
INSERT INTO password_groups_snippets
(group_id, title, val_enc)
VALUES (:group-id, :title, :val-enc)

-- :name delete-group-snippet-by-id! :! :n
-- :doc removes a single group snippet by its id
DELETE FROM password_groups_snippets
WHERE group_id = :group-id and id = :id

-- :name update-group-snippet! :! :n
-- :doc update all
UPDATE password_groups_snippets
SET
title = :title,
val_enc = :val-enc
WHERE group_id = :group-id AND id = :id


----

-- :name select-group-certs-lbls :? :*
-- :doc retrieves labels and ids from the password_groups_certs
SELECT id, gid, lbl FROM password_groups_certs
WHERE group_id = :group-id
ORDER BY lbl


-- :name get-group-cert-by-id :? :1
-- :doc retrieves a single group cert by its id
SELECT * FROM password_groups_certs
WHERE group_id = :group-id AND gid = :gid

-- :name get-group-cert-lbl-by-id :? :1
-- :doc retrieves a single group cert by its id
SELECT id, gid, lbl FROM password_groups_certs
WHERE group_id = :group-id AND gid = :gid


-- :name select-group-certs-by-lbls :? :*
-- :doc retrieves all certs for the lbls provides
SELECT * FROM password_groups_certs
WHERE group_id = :group-id
AND lbl IN (:v*:lbls)
ORDER BY lbl

-- :name insert-group-cert! :i!
-- :doc insert a new password_groups_certs
INSERT INTO password_groups_certs
(group_id, assigned_to, lbl, pub_key_comp, val_enc)
VALUES (:group-id, :user-assigned, :lbl, :pub-key-comp, :val-enc)

-- :name delete-group-cert-by-id! :! :n
-- :doc removes a single group cert by its id
DELETE FROM password_groups_certs
WHERE group_id = :group-id AND id = :id

-- :name update-group-cert! :! :n
-- :doc update all
UPDATE password_groups_certs
SET
lbl = :lbl,
pub_key_comp = :pub-key-comp,
val_enc = :val-enc
WHERE group_id = :group-id AND id = :id


----

-- :name select-group-envs-lbls :? :*
-- :doc retrieves labels and ids from the password_groups_envs
SELECT id, gid, lbl, description FROM password_groups_envs
WHERE group_id = :group-id
ORDER BY lbl

-- :name select-group-envs-by-lbls :? :*
-- :doc retrieves all envs for the lbls provides
SELECT * FROM password_groups_envs
WHERE group_id = :group-id
AND lbl IN (:v*:lbls)
ORDER BY lbl

-- :name get-group-env-by-id :? :1
-- :doc retrieves a single group env by its id
SELECT * FROM password_groups_envs
WHERE group_id = :group-id AND gid = :gid

-- :name get-group-env-lbl-by-id :? :1
-- :doc retrieves a single group env by its id
SELECT id, gid, lbl FROM password_groups_envs
WHERE group_id = :group-id AND gid = :gid

-- :name insert-group-env! :i!
-- :doc insert a new password_groups_envs
INSERT INTO password_groups_envs
(group_id, lbl, val_enc, description)
VALUES (:group-id, :lbl, :val-enc, :description)

-- :name delete-group-env-by-id! :! :n
-- :doc removes a single group env by its id
DELETE FROM password_groups_envs
WHERE group_id = :group-id AND id = :id

-- :name update-group-env! :! :n
-- :doc update all
UPDATE password_groups_envs
SET
lbl = :lbl,
val_enc = :val-enc,
description = :description
WHERE group_id = :group-id AND id = :id


------

-- :name update-account-info! :! :n
/* :require [clojure.string :as string]
            [hugsql.parameters :refer [identifier-param-quote]] */
update users set
/*~
(string/join ","
  (for [[field _] (:updates params)]
    (str (identifier-param-quote (name field) options)
      " = :v:updates." (name field))))
~*/
where id = :id


----------
-- :name insert-customer! :i!
-- :doc create a stripe customer user relation
INSERT INTO user_customer
(user_id, stripe_id)
VALUES (:user-id, :stripe-id)

-- :name get-user-customer :? :1
-- :doc retrieves the customer id if any for the user
SELECT stripe_id FROM user_customer
WHERE user_id = :user-id

-- :name update-customer-id! :! :n
-- :doc update all
UPDATE user_customer
SET
stripe_id = :new-stripe-id,
WHERE user_id = :user-id AND
stripe_id = :stripe-id

-- :name delete-user-customer-rel! :! :n
-- :doc removes a user customer relation
DELETE FROM user_customer
WHERE user_id = :user-id AND stripe_id = :stripe-id

-----------

-- :name insert-payment-src! :i!
-- :doc create a stripe payment user relation
INSERT INTO user_payment_src
(user_id, stripe_id, card_name, card_exp, card_last_4)
VALUES (:user-id, :stripe-id, :card-name, :card-exp, :card-last-4)

-- :name get-user-payment-src :? :1
-- :doc retrieves the payment-src for the user
SELECT * FROM user_payment_src
WHERE user_id = :user-id

-- :name update-payment-src-id! :! :n
-- :doc update all
UPDATE user_payment_src
SET
stripe_id = :new-stripe-id,
card_exp = :card-exp,
card__name = :card-name,
card_last_4 = :card-last-4
WHERE user_id = :user-id AND
stripe_id = :stripe-id

-- :name delete-user-payment-rel! :! :n
-- :doc removes a user payment rel
DELETE FROM user_payment_src
WHERE user_id = :user-id AND stripe_id = :stripe-id


------------


-- :name insert-product! :i!
-- :doc create a stripe product entry
INSERT INTO products
(stripe_id, name)
VALUES (:stripe-id, :name)


-- :name get-products :? :*
-- :doc retrieves products
SELECT * FROM products
WHERE name LIKE :name-like


-- :name insert-product-plan! :i!
-- :doc create a stripe product entry
INSERT INTO product_plans
(product_id, stripe_id, name, type, cost, curr, max_users, max_vaults, max_logins, max_secrets, max_envs, max_certs, max_snippets)
VALUES (:product-id, :stripe-id, :name, :type, :cost, :curr, :max-users, :max-vaults, :max-logins, :max-secrets, :max-envs, :max-certs, :max-snippets)

-- :name get-product-plans :? :*
-- :doc retrieves products
SELECT * FROM product_plans
WHERE name LIKE :name-like


-- :name get-product-plan-by-name :? :*
-- :doc retrieves products
SELECT * FROM product_plans
WHERE LOWER(name) LIKE LOWER(:name)


---------------

-- :name insert-subscription! :i!
-- :doc create a stripe subscription
INSERT INTO subscriptions
(user_id, plan_id, customer_stripe_id, stripe_id, start_date, end_date)
VALUES (:user-id, :plan-id, :customer-stripe-id, :stripe-id, :start-date, :end-date)


-- :name update-subscription! :! :n
/* :require [clojure.string :as string]
            [hugsql.parameters :refer [identifier-param-quote]] */
update subscriptions set
/*~
(string/join ","
  (for [[field _] (:updates params)]
    (str (identifier-param-quote (name field) options)
      " = :v:updates." (name field))))
~*/
where id = :id

-- :name get-user-subscriptions :? :*
-- :doc retrieves all the user subscriptions with product plans joined up, ordered desc by start_date
SELECT
 s.id,
 s.stripe_id,
 s.start_date,
 s.end_date,

 p.id as product_id,
 p.stripe_id as product_stripe_id,

 p.name,
 p.type,
 p.cost,
 p.max_users,
 p.max_vaults,
 p.max_secrets,
 p.max_envs,
 p.max_certs,
 p.max_snippets,
 p.max_logins,
 p.curr,
 p.cost
 FROM subscriptions s
LEFT JOIN product_plans p on s.plan_id = p.id
WHERE user_id = :user-id
ORDER BY start_date, end_date desc


-- :name delete-subscription! :! :n
-- :doc deletes a subscription by id
DELETE FROM subscriptions
WHERE id = :id

----------

-- :name get-user-reset-codes :? :*
-- :doc retrieves user reset codes
SELECT * FROM user_reset_codes
WHERE user_id = :user-id


-- :name select-user-reset-code-count :? :1
-- :doc retrieves user reset code count
SELECT COUNT(*) FROM user_reset_codes
WHERE user_id = :user-id

-- :name insert-user-reset-code! :i!
-- :doc create a stripe subscription, code-hash is the token code's hash
INSERT INTO user_reset_codes
(user_id, code_hash, enc_key)
VALUES (:user-id, :code-hash, :enc-key)

-- :name delete-user-reset-codes! :! :n
-- :doc deletes all user reset codes
DELETE FROM user_reset_codes
WHERE user_id = :user-id


-- :name delete-user-reset-code! :! :n
-- :doc deletes a specific reset code by id
DELETE FROM user_reset_codes
WHERE id = :id



--------- app keys

-- :name delete-user-app-key! :! :n
-- :doc removes a user app key
DELETE FROM app_keys
WHERE user_id = :user-id AND id = :id

-- :name create-user-app-key! :! :n
-- :doc creates a new user app key
INSERT INTO app_keys
(user_id, key_id, key_secret_hash, enc_key, date_expire)
VALUES (:user-id, :key-id, :key-secret-hash, :enc-key, :date-expire)

-- :name get-user-app-keys :? :*
-- :doc retrieves user app keys
SELECT * FROM app_keys
WHERE user_id = :user-id

-- :name get-user-app-key-by-id :? :*
-- :doc retrieves user app key and user data for the key
SELECT * FROM app_keys k
JOIN users u on u.id = k.user_id
WHERE key_id = :key-id;



-- :name create-user-visit! :! :n
-- :doc creates a new user visit entry
INSERT INTO user_visits
(user_id, event_time, ref_url, url, event)
VALUES (:user-id, timezone('utc', now()), :ref-url, :url, :event)

-- :name get-user-visits :? :*
-- :doc retrieves user visits with the user info joined in
SELECT u.user_name, k.* FROM user_visits k
JOIN users u on u.id = k.user_id
WHERE k.user_id = :user-id;

