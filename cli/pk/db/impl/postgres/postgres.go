package postgres

import (
	"database/sql"
	"fmt"
	"github.com/lib/pq"
	"io"
	"pk/db/impl"
	io2 "pk/db/io"
	"pk/db/types"
	"pk/log"
	"pk/swagger"
	"strings"
)

var DESC_TABLE = "select column_name, data_type, character_maximum_length from INFORMATION_SCHEMA.COLUMNS where table_name = '%s'"

var SHOWTABLES = "SELECT table_schema || '.' || table_name FROM information_schema.tables WHERE table_type = 'BASE TABLE' AND table_schema NOT IN ('pg_catalog', 'information_schema');"

type SqlConnector struct {
	Db *sql.DB
	ConnStr string
}

func (sqlDb SqlConnector) ListTables() ([]string, error) {

	rows, err := sqlDb.Db.Query(SHOWTABLES)

	if err != nil {
		return nil, err
	}

	defer rows.Close()

	var tables []string

	for rows.Next() {

		var tableName string
		err := rows.Scan(&tableName)

		if err != nil {
			return nil, err
		}
		tables = append(tables, tableName)
	}

	err = rows.Err()
	if err != nil {
		return nil, err
	}

	return tables, nil
}

func (sqlDb SqlConnector) Dump(db *swagger.Db, table string, csvDumpOptions *io2.CSVDumpOptions, writer io.Writer) error {

	return impl.Dump(sqlDb.Db, table, csvDumpOptions, writer)

}

func (sqlDb SqlConnector) Copy(db *swagger.Db, table string, csvCopyOptions *io2.CSVCopyOptions, reader *io.Reader) error {

	buffReader := io2.OpenCSVReader(csvCopyOptions.Separator, reader)

	columns, err := io2.GetHeader(csvCopyOptions.SkipFirstLine, csvCopyOptions.Header, func() ([]string, error) {
		return buffReader.Read()
	})

	if err != nil {
		return err
	}

	if len(columns) == 0 {
		return fmt.Errorf("header columns must be specified")
	}

	log.Debugf("Import data using columns: %s", columns)

	txn, err := sqlDb.Db.Begin()

	if err != nil {
		return err
	}

	var stmt *sql.Stmt

	tblNameSchema := strings.Split(table, ".")
	if len(tblNameSchema) > 1 {
		stmt, err = txn.Prepare(pq.CopyInSchema(tblNameSchema[0], tblNameSchema[1], columns...))
	} else {
		stmt, err = txn.Prepare(pq.CopyIn(table, columns...))
	}

	if err != nil {
		return err
	}

	//we will store the current line's values in this holder
	//the stmt.Exec iterates over it and returns a new copy of driver named values
	var args = make([]interface{}, len(columns))

	for {
		line, err := buffReader.Read()

		if err != nil {
			break
		}

		if len(line) == 0 {
			continue
		}

		if len(line) != len(columns) {
			err = fmt.Errorf("got %d values in line, but only have %d columns as header specified", len(line), len(columns))
			break
		}

		//copy string into args of interface type
		var lineVal string

		for i := range columns {
			lineVal = line[i]

			if lineVal == "" {
				args[i] = nil
			} else {
				args[i] = lineVal
			}
		}

		_, err = stmt.Exec(args...)

		if err != nil {
			break
		}
	}

	if err == io.EOF {
		err = nil
	}

	if err != nil {

		//close and ignore errors
		stmt.Close()

		return err
	}


	if err != nil {
		return err
	}

	//close statement
	err = stmt.Close()

	if err != nil {
		return err
	}

	//commit the transaction
	err = txn.Commit()

	if err != nil {
		return err
	}

	return err
}


func CreatePostgresConnector(db swagger.Db, sslMode string) (types.DBConnector, error) {

	if sslMode == "" {
		sslMode = "disable"
	}

	//connect to the postgres compatible Db
	connStr := fmt.Sprintf("postgres://%s:%s@%s:%s/%s?sslmode=%s",
		db.Dbuser, db.Password, db.Host, db.Port, db.Database, sslMode)

	sqlDB, err := sql.Open("postgres", connStr)

	if err != nil {
		return nil, err
	}

	return SqlConnector{
		Db: sqlDB,
		ConnStr: connStr,
	}, nil
}

func (sqlDb SqlConnector) EofChar() string {
	return ";"
}
