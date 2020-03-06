
ALTER TABLE user_visits ADD COLUMN event smallint default 0;

ALTER TABLE user_visits DROP CONSTRAINT "user_visits_user_id_fkey";