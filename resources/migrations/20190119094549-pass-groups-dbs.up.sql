CREATE TABLE IF NOT EXISTS password_groups_dbs
(
 id SERIAL PRIMARY KEY,
 group_id BIGINT NOT NULL REFERENCES password_groups(id) ON DELETE CASCADE,
 hosted_on VARCHAR(300),
 lbl VARCHAR(300) DEFAULT '-',
 type VARCHAR(50) DEFAULT 'other',
 val_enc bytea NOT NULL,

 UNIQUE (group_id, lbl)
);