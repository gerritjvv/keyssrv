-- :disable-transaction

CREATE TABLE IF NOT EXISTS users
(id SERIAL PRIMARY KEY,
 user_name VARCHAR(100) UNIQUE NOT NULL,
 email VARCHAR(100) UNIQUE NOT NULL,
 pass_hash bytea NOT NULL, --hash of the user's password, used for login and authentication
 mfa_key_enc bytea DEFAULT NULL , -- 2fa key encrypted with the server's public key -- for future.
 confirmed BOOLEAN DEFAULT FALSE, -- if the email authentication was completed
 org BOOLEAN DEFAULT FALSE, -- true if the user is an organisation i.e an account, for future usage.
 enc_key bytea NOT NULL, -- the encrypted with pass key that the user uses to encrypt group and other keys
 wiz smallint DEFAULT NULL, -- wizzard
 wiz_step smallint DEFAULT NULL -- step inside the wizzard,
 );

--;;

CREATE TABLE IF NOT EXISTS user_reset_codes
(
 id SERIAL PRIMARY KEY,
 user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 code_hash bytea NOT NULL,
 enc_key bytea NOT NULL
);

--;;


-- a group must be unique for a user+name combination,
-- we assign an id for convinience
CREATE TABLE IF NOT EXISTS password_groups
(
 id SERIAL PRIMARY KEY,
 user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE, -- ownwer user
 name VARCHAR(300) NOT NULL,
 UNIQUE(user_id, name)
);

--;;

CREATE TABLE IF NOT EXISTS password_groups_users
(
 owner BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 group_id BIGINT NOT NULL REFERENCES password_groups(id) ON DELETE CASCADE,
 user_group_master_key_enc bytea NOT NULL, -- The master key for the group encrypted with the user's password
 is_admin BOOLEAN DEFAULT True, -- true if the user is admin (write + invite permissions), otherwise readonly

 confirmed BOOLEAN DEFAULT True, -- when false the user_group_master_key_enc is encrypted with the system key

 PRIMARY KEY (owner, user_id, group_id)
);

--;;

CREATE TABLE IF NOT EXISTS password_groups_secrets
(
 group_id BIGINT NOT NULL REFERENCES password_groups(id) ON DELETE CASCADE,

 id SERIAL PRIMARY KEY,

 lbl VARCHAR(300) NOT NULL,
 val_enc bytea NOT NULL,

 UNIQUE (group_id, lbl)
);

--;;

CREATE TABLE IF NOT EXISTS password_groups_logins
(
 id SERIAL PRIMARY KEY,
 group_id BIGINT NOT NULL REFERENCES password_groups(id) ON DELETE CASCADE,
 login VARCHAR(300) NOT NULL,
 user_name VARCHAR(300) NOT NULL,
 user_name2 VARCHAR(300) NOT NULL,
 val_enc bytea NOT NULL,

 UNIQUE (group_id, login, user_name, user_name2)
);

--;;

CREATE TABLE IF NOT EXISTS password_groups_snippets
(
 id SERIAL PRIMARY KEY,
 group_id BIGINT NOT NULL REFERENCES password_groups(id) ON DELETE CASCADE,
 title VARCHAR(300) NOT NULL,
 val_enc bytea NOT NULL,

 UNIQUE (group_id, title)
);

--;;

CREATE TABLE IF NOT EXISTS password_groups_certs
(
 id SERIAL PRIMARY KEY,
 group_id BIGINT NOT NULL REFERENCES password_groups(id) ON DELETE CASCADE,
 lbl VARCHAR(300) NOT NULL,
 pub_key_comp bytea, -- public certificate key compressed
 val_enc bytea,  -- private certificate key encrypted

 assigned_to BIGINT NULL REFERENCES users(id) ON DELETE CASCADE,

 UNIQUE (group_id, lbl)
);

--;;

CREATE TABLE IF NOT EXISTS password_groups_envs
(
 id SERIAL PRIMARY KEY,
 group_id BIGINT NOT NULL REFERENCES password_groups(id) ON DELETE CASCADE,
 lbl VARCHAR(300) NOT NULL,
 val_enc bytea,  -- private env encrypted file

 UNIQUE (group_id, lbl)
);


---;;

CREATE TABLE IF NOT EXISTS user_payment_src
(
 id SERIAL PRIMARY KEY,
 user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 card_name VARCHAR(300),
 card_exp CHAR(10),
 card_last_4 CHAR(4),

 stripe_id VARCHAR(300) NOT NULL,

 UNIQUE (user_id, stripe_id)
);

CREATE TABLE IF NOT EXISTS products
(
 id SERIAL PRIMARY KEY,
 stripe_id VARCHAR(300) NOT NULL,
 name VARCHAR(300) NOT NULL,
 UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS product_plans
(
 id SERIAL PRIMARY KEY,
 product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
 stripe_id VARCHAR(300) NOT NULL,
 name VARCHAR(300) NOT NULL,
 type VARCHAR(100) NOT NULL, -- flexible enum free,basic,pro

 cost DECIMAL NOT NULL,
 curr CHAR(3) NOT NULL ,
 max_users INT NOT NULL,
 max_vaults INT NOT NULL,
 max_secrets INT NOT NULL,
 max_envs INT NOT NULL,
 max_certs INT NOT NULL,
 max_snippets INT NOT NULL,
 max_logins INT NOT NULL,

 UNIQUE (product_id, stripe_id),
 UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS user_customer
(
 user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 stripe_id VARCHAR(300) NOT NULL,
 PRIMARY KEY (user_id, stripe_id)
);

CREATE TABLE IF NOT EXISTS subscriptions
(
 id SERIAL PRIMARY KEY,
 user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 plan_id BIGINT NOT NULL REFERENCES product_plans(id) ON DELETE CASCADE,
 customer_stripe_id VARCHAR(300) NOT NULL, -- the customer object stripe id
 stripe_id VARCHAR(300) NOT NULL, -- the subscription stripe id
 start_date timestamp NOT NULL, -- start when the subscription entry was made, subs are billed monthly next_date=curr_date+month
 end_date timestamp, -- null end date means rolling subscription
 UNIQUE (user_id, start_date, end_date)
);
