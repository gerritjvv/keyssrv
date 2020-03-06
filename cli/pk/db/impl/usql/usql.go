package usql

import (
	"fmt"
	"io"
	"pk/db/impl/mysql"
	"pk/db/impl/postgres"
	io2 "pk/db/io"
	"pk/db/types"
	"pk/swagger"
	"strings"
)

type USQLConnector struct {
	ConnStr string

	PostgresConnector *types.DBConnector
	MysqlConnector    *types.DBConnector

	ReplVars [][]string //array of tuples [(str, str)]
}

func (sqlDb USQLConnector) ListTables() ([]string, error) {
	if sqlDb.MysqlConnector != nil {
		dbConnector := (*sqlDb.MysqlConnector)
		return dbConnector.ListTables()
	}

	if sqlDb.PostgresConnector != nil {
		dbConnector := (*sqlDb.PostgresConnector)
		return dbConnector.ListTables()
	}

	return nil, fmt.Errorf("This action (list tables) is only implemented for postgres and mysql compatible databases")
}

func (sqlDb USQLConnector) Copy(db *swagger.Db, table string, csvCopyOptions *io2.CSVCopyOptions, reader *io.Reader) error {

	//We have a postgres special copy implementation
	// this uses the postgres optimized copy
	if sqlDb.PostgresConnector != nil {
		dbConnector := (*sqlDb.PostgresConnector)
		return dbConnector.Copy(db, table, csvCopyOptions, reader)
	}

	//We have a mysql special copy implementation
	// this uses the mysql optimized copy
	if sqlDb.MysqlConnector != nil {
		dbConnector := (*sqlDb.MysqlConnector)
		return dbConnector.Copy(db, table, csvCopyOptions, reader)
	}

	return fmt.Errorf("DB copy is currently only implemented for postgres and mysql compatible databases.\nPlease file an issue https://github.com/pkhubio/pkcli to add support for your db type.\n")
}

func (sqlDb USQLConnector) Dump(db *swagger.Db, table string, csvDumpOptions *io2.CSVDumpOptions, writer io.Writer) error {

	//We have a postgres special copy implementation
	// this uses the postgres optimized copy
	if sqlDb.PostgresConnector != nil {
		dbConnector := (*sqlDb.PostgresConnector)
		return dbConnector.Dump(db, table, csvDumpOptions, writer)
	}

	//We have a mysql special copy implementation
	// this uses the mysql optimized copy
	if sqlDb.MysqlConnector != nil {
		dbConnector := (*sqlDb.MysqlConnector)
		return dbConnector.Dump(db, table, csvDumpOptions, writer)
	}

	return fmt.Errorf("DB dump is currently only implemented for postgres and mysql compatible databases.\nPlease file an issue https://github.com/pkhubio/pkcli to add support for your db type.\n")

}

func CreateConnector(db swagger.Db, sslMode string) (*USQLConnector, error) {

	driver := getDriver(db)

	if driver == "" {
		return nil, fmt.Errorf("Driver %s not supported yet.", db.Type_)
	}

	var connStr string
	if driver == "postgres" {
		sslMode1 := sslMode
		if sslMode == "" {
			sslMode1 = "allow"
		}

		connStr = fmt.Sprintf("%s://%s:%s@%s:%s/%s?sslmode=%s",
			driver,
			db.Dbuser, db.Password, db.Host, db.Port, db.Database, sslMode1)
	} else {
		connStr = fmt.Sprintf("%s://%s:%s@%s:%s/%s",
			driver,
			db.Dbuser, db.Password, db.Host, db.Port, db.Database)

	}

	var postgresConnector *types.DBConnector
	var mysqlConnector *types.DBConnector

	replVars := make([][]string, 0)

	if driver == "postgres" {
		postgresConnector1, err := postgres.CreatePostgresConnector(db, sslMode)

		if err != nil {
			return nil, err
		}
		postgresConnector = &postgresConnector1

		replVars = createPostgresReplVars(replVars)
	}

	if driver == "mysql" {
		mysqlConnector1, err := mysql.CreateMysqlConnector(db, sslMode)

		if err != nil {
			return nil, err
		}
		mysqlConnector = &mysqlConnector1
	}

	return &USQLConnector{ReplVars: replVars, ConnStr: connStr, PostgresConnector: postgresConnector, MysqlConnector: mysqlConnector}, nil
}

//Add variables that can supplement the postgres command like \d become :d
func createPostgresReplVars(replVars [][]string) [][]string {

	replVars = append(replVars, []string{
		"d", postgres.SHOWTABLES,
		"t", postgres.DESC_TABLE})

	return replVars
}

func getDriver(db swagger.Db) interface{} {
	switch strings.ToLower(strings.TrimSpace(db.Type_)) {
	case "postgres":
		return "postgres"
	case "redshift":
		return "redshift"
	case "mysql":
		return "mysql"
	case "cockroachdb":
		return "cockroach"
	case "tidb":
		return "mysql"
	case "vitess":
		return "mysql"
	case "memsql":
		return "mysql"
	default:
		return ""
	}
}

func (sqlDb USQLConnector) EofChar() string {
	return ";"
}
