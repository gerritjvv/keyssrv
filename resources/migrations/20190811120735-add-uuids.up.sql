

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

alter table users add column if not exists gid uuid DEFAULT gen_random_uuid();
--;;

alter table password_groups add column if not exists gid uuid DEFAULT gen_random_uuid();
--;;

alter table password_groups_certs add column if not exists gid uuid DEFAULT gen_random_uuid();
--;;

alter table password_groups_dbs add column if not exists gid uuid DEFAULT gen_random_uuid();
--;;

alter table password_groups_envs add column if not exists gid uuid DEFAULT gen_random_uuid();
--;;

alter table password_groups_logins add column if not exists gid uuid DEFAULT gen_random_uuid();
--;;

alter table password_groups_secrets add column if not exists gid uuid DEFAULT gen_random_uuid();
--;;

alter table password_groups_snippets add column if not exists gid uuid DEFAULT gen_random_uuid();
--;;

alter table app_keys add column if not exists gid uuid DEFAULT gen_random_uuid();
--;;

CREATE UNIQUE INDEX IF NOT EXISTS idx_gid_users ON users(gid);
--;;

CREATE UNIQUE INDEX IF NOT EXISTS idx_gid_password_groups ON password_groups(gid);
--;;

CREATE UNIQUE INDEX IF NOT EXISTS idx_gid_password_groups_certs ON password_groups_certs(gid);
--;;

CREATE UNIQUE INDEX IF NOT EXISTS idx_gid_password_groups_dbs ON password_groups_dbs(gid);
--;;

CREATE UNIQUE INDEX IF NOT EXISTS idx_gid_password_groups_envs ON password_groups_envs(gid);
--;;

CREATE UNIQUE INDEX IF NOT EXISTS idx_gid_password_groups_logins ON password_groups_logins(gid);
--;;

CREATE UNIQUE INDEX IF NOT EXISTS idx_gid_password_groups_secrets ON password_groups_secrets(gid);
--;;

CREATE UNIQUE INDEX IF NOT EXISTS idx_gid_password_groups_snippets ON password_groups_snippets(gid);
--;;

CREATE UNIQUE INDEX IF NOT EXISTS idx_gid_app_keys ON app_keys(gid);
--;;






