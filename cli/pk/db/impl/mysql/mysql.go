package mysql

import (
	"bufio"
	"database/sql"
	"fmt"
	"io"
	"pk/db/impl"
	"pk/db/impl/postgres"
	io2 "pk/db/io"
	"pk/db/types"
	"pk/log"
	"pk/swagger"
	"strings"

	"github.com/go-sql-driver/mysql"
)

type MySqlConnector struct {
	*postgres.SqlConnector
}

// Read a single line from the reader and return split on sep
func readCSVLine(reader *bufio.Reader, sep string) ([]string, error) {
	line, _, err := reader.ReadLine()

	if err != nil {
		return nil, err
	}

	return strings.Split(string(line), sep), err
}

func (sqlDb MySqlConnector) ListTables() ([]string, error) {
	rows, err := sqlDb.Db.Query("SHOW TABLES")

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


func (sqlDb MySqlConnector) Dump(db *swagger.Db, table string, csvDumpOptions *io2.CSVDumpOptions, writer io.Writer) error {
	return impl.Dump(sqlDb.Db, table, csvDumpOptions, writer)
}

func (sqlDb MySqlConnector) Copy(db *swagger.Db, table string, csvCopyOptions *io2.CSVCopyOptions, reader *io.Reader) error {

	buffReader := bufio.NewReader(*reader)


	descCols, descColTypes, err := queryTableDataTypes(sqlDb.Db, table);

	if err != nil {
		return err
	}

	//return the input headers from the command line param or the first line of the file
	columns, err := io2.GetHeader(csvCopyOptions.SkipFirstLine, csvCopyOptions.Header, func() ([]string, error) {
		return readCSVLine(buffReader, csvCopyOptions.Separator)
	})

	if err != nil {
		return err
	}

	fieldSep := ","

	if csvCopyOptions.Separator != "" {
		fieldSep = csvCopyOptions.Separator
	}


	var fieldsEnclosed string
	if csvCopyOptions.FieldsEnclosedBy != "" {
		fieldsEnclosed = fmt.Sprintf("ENCLOSED BY '%s'", csvCopyOptions.FieldsEnclosedBy)
	} else {
		fieldsEnclosed = "OPTIONALLY ENCLOSED BY '\"'"
	}


	mysql.RegisterReaderHandler("data", func() io.Reader {
		return buffReader
	})

	// Its important that we correctly detect binary data in the csv file and destination column
	// we check the destination column types and if a binary value is expected, we expect the
	// csv source to be in hex. Then we assign the value to a variable e.g @var1 in the load
	// and set the column using UNHEX (  @var1 ), We also use trim to remove \x from the start
	// of the csv data if it exists because UNHEX does not process the \x prefix correctly.
	// if the table destination column is anything but a binary value the string hex representation
	// of the data is imported and not the binary value
	fields, fieldSets, err := makeTransformSql(columns, descCols, descColTypes)

	if err != nil {
		return err
	}

	sql2 := fmt.Sprintf("LOAD DATA LOCAL INFILE 'Reader::data' INTO TABLE %s FIELDS TERMINATED BY '%s' %s LINES TERMINATED BY '\n' (%s) %s",
		table,
		fieldSep,
		fieldsEnclosed,
		strings.Join(fields, ","),
		fieldSets,
		)

	log.Debugf("Running SQL: %s", sql2)

	_, err = sqlDb.Db.Exec(sql2)

	if err != nil {
		if strings.Contains(err.Error(), "The used command is not allowed with this MySQL version") {
			return fmt.Errorf("%s, The LOCAL INFILE command can only be used if mysql has local-infile=1, by default his is disabled.", err)
		}
		return err
	}

	return nil
}

// the fields to load and any set values in the string
// (name, id, @var1) set data = UNHEX(@var1)
func makeTransformSql(inputCols []string, descCols []string, columnTypes []*sql.ColumnType) ([]string, string, error) {

	if len(descCols) != len(inputCols) {
		return nil, "", fmt.Errorf("The csv columns do not match the destination columns. Input columns %s != destination columns %s",
			strings.Join(inputCols, ","),
			strings.Join(descCols, ","))
	}

	/*
	 We use the index of the inputCols because the order of the csv input columns may not match that of the database destination columns
	 */
	var sb strings.Builder
	fields := make([]string, len(inputCols))

	inputColsLower := AllTopLower(inputCols)

	for i, col := range inputCols {

		index, err := IndexOf(inputColsLower, strings.ToLower(descCols[i]))
		if err != nil {
			return nil, "", fmt.Errorf("The csv columns do not match that of the destination. Input column %s != destination column %s", col, descCols[i])
		}

		if impl.IsBinaryType(columnTypes[i]) {
			// in case of a binary field, we expect the source to be hex encoded
			// we then assign to a variable and set the column with UNHEX
			// see https://stackoverflow.com/questions/12038814/import-hex-binary-data-into-mysql
			fieldVar := fmt.Sprintf("@var%d", i)

			fields[index] = fieldVar
			sb.WriteString(fmt.Sprintf(" SET %s = UNHEX(TRIM( LEADING '%s' from %s)) ",
				descCols[i],
				"\x5c\x78", // \x in hex
				fieldVar))

		} else {
			fields[index] = descCols[i]
		}

	}

	return fields, sb.String(), nil
}

/*
 Make all the strings lower case
 */
func AllTopLower(inputStr []string) []string{
	v := make([]string, len(inputStr))

	for i, s := range inputStr {
		v[i] = strings.ToLower(s)
	}

	return v
}

// return the index of v in s or -1
func IndexOf(s []string, v string) (int, error) {
	for i := range s {
		if v == s[i] {
			return i, nil
		}
	}

	return -1, fmt.Errorf("Cannot find index for %s", v)
}

// Get the table column and column types
func queryTableDataTypes(db *sql.DB, table string) ([]string, []*sql.ColumnType, error){

	rows, err := db.Query("SELECT * FROM " + table + " WHERE false")

	if err != nil {
		return nil, nil, err
	}

	defer rows.Close()

	cols, err := rows.Columns()
	if err != nil {
		return nil, nil, err
	}

	colTypes, err := rows.ColumnTypes()

	if err != nil {
		return cols, colTypes, nil
	}

	return cols, colTypes, nil
}

func CreateMysqlConnector(db swagger.Db, _ string) (types.DBConnector, error) {

	//connect to the postgres compatible Db
	connStr := fmt.Sprintf("%s:%s@tcp(%s:%s)/%s",
		db.Dbuser, db.Password, db.Host, db.Port, db.Database)

	sqlDB, err := sql.Open("mysql", connStr)

	if err != nil {
		return nil, err
	}

	return MySqlConnector{SqlConnector: &postgres.SqlConnector{Db: sqlDB}}, nil
}
