
CREATE TABLE IF NOT EXISTS wizzard_data (
 id SERIAL PRIMARY KEY,
 user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 plan varchar not null default 'free',
 plan_period varchar not null default 'year',
 UNIQUE (user_id)
)