ALTER TABLE users ADD COLUMN IF NOT EXISTS aws_client_id varchar DEFAULT NULL;
--;;

ALTER TABLE users ADD COLUMN IF NOT EXISTS aws_product_id varchar DEFAULT NULL;
--;;
-- status should be subscribe-success, subscribe-canceled, subscribe-pending, nil
ALTER TABLE users ADD COLUMN IF NOT EXISTS aws_sub_status varchar DEFAULT NULL;
--;;

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_aws_client_id ON users(aws_client_id);
--;;