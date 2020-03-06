alter table password_groups_logins alter column login drop not null;
alter table password_groups_logins alter column user_name drop not null;
alter table password_groups_logins alter column user_name2 drop not null;
