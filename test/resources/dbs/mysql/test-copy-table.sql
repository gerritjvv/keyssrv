
-- :name set-infile-on!
-- :command :execute
-- :result :raw
-- :doc set the global local_infile variable to on
SET GLOBAL local_infile = 'ON';

-- :name create-table-a!
-- :command :execute
-- :result :raw
-- :doc Create a test table
create table if not exists table_a (
  id int not null auto_increment,
  name       varchar(40),
  created_at timestamp not null default current_timestamp,
  enabled    bool,
  primary key(id)
)


-- :name get-table-a-records :? :*
-- :doc Get all table a records
select * from table_a