

-- UNIQUE (group_id, login, user_name, user_name2)


ALTER TABLE password_groups_logins
RENAME COLUMN login TO lbl;

ALTER TABLE password_groups_logins
ADD COLUMN login VARCHAR(300);


CREATE UNIQUE INDEX password_groups_logins_group_id_lbl_key ON password_groups_logins (group_id, lbl);
ALTER TABLE password_groups_logins DROP CONSTRAINT password_groups_logins_group_id_login_user_name_user_name2_key;
