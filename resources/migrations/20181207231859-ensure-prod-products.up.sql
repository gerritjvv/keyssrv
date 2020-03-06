
-- Production product plans that map to tke pkhub.io pricing plans

-- INSERT INTO products (stripe_id, name) VALUES('prod_E76jrfrJtIjm8N', 'pkhub') ON CONFLICT ON CONSTRAINT products_name_key DO NOTHING
--
-- INSERT INTO product_plans
-- (product_id, stripe_id, name, type, cost, curr, max_users, max_vaults, max_secrets, max_logins, max_envs, max_certs, max_snippets)
-- VALUES(1, 'plan_E76jMrgV3EPMTR', 'Prod-Free', 'free', 0.00,  'USD', 3, 2, 10, 10, 2, 2, 2) ON CONFLICT ON CONSTRAINT product_plans_name_key DO NOTHING;
--
-- INSERT INTO product_plans
-- (product_id, stripe_id, name, type, cost, curr, max_users, max_vaults, max_secrets, max_envs, max_certs, max_snippets, max_logins)
-- VALUES(1, 'plan_E76kLrb872HtcQ', 'Prod-Basic', 'basic', 4.99, 'USD', 25, 10, 100, 10, 10, 10, 100) ON CONFLICT ON CONSTRAINT product_plans_name_key DO NOTHING;
--
-- INSERT INTO product_plans
-- (product_id, stripe_id, name, type, cost, curr, max_users, max_vaults, max_secrets, max_envs, max_certs, max_snippets, max_logins)
-- VALUES(1, 'plan_E76lXpR2tCnyTV', 'Prod-Pro', 'pro', 7.99, 'USD', 1000, 1000, 1000, 1000, 1000, 1000, 1000) ON CONFLICT ON CONSTRAINT product_plans_name_key DO NOTHING;
