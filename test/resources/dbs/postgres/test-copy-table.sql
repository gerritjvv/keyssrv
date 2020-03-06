-- :name create-table-a!
-- :command :execute
-- :result :raw
-- :doc Create a test table
create table if not exists table_a (
  id         serial primary key,
  name       varchar(40),
  created_at timestamp not null default current_timestamp,
  enabled    bool
)


-- :name get-table-a-records :? :*
-- :doc Get all table a records
select * from table_a