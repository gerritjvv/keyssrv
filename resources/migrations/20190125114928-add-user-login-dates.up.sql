
CREATE TABLE IF NOT EXISTS user_visits
(id SERIAL PRIMARY KEY,
 user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 event_time timestamp NOT NULL,
 ref_url text DEFAULT NULL, -- this is for logins on page /
 url varchar(255) NOT NULL
 );

CREATE INDEX IF NOT EXISTS user_visists_dates ON user_visits (user_id, event_time);