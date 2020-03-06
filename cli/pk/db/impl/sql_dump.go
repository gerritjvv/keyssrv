/*
 Generic SQL dump funtion.

 Postgres performance:
   Strangely enough the copy to stdout sql works slower than just doing a select * from table
 */
package impl

import (
	"database/sql"
	"encoding/csv"
	"encoding/hex"
	"fmt"
	"io"
	io2 "pk/db/io"
	"strings"
)

/*
 Does a select * from on the table and writes out it rows via the csv writer to io.Writer
 */
func Dump(sqlDb *sql.DB, table string, csvDumpOptions *io2.CSVDumpOptions, writer io.Writer) error {

	rows, err := sqlDb.Query(fmt.Sprintf("select * from %s", table))
	if err != nil {
		return err
	}

	defer rows.Close()

	cols, err := rows.Columns()

	if err != nil {
		return err
	}

	colTypes, err := rows.ColumnTypes()
	if err != nil {
		return err
	}

	csvWriter := csv.NewWriter(writer)
	defer csvWriter.Flush()

	if csvDumpOptions.Separator != "" {
		csvWriter.Comma = []rune(csvDumpOptions.Separator)[0]
	}

	colLen := len(cols)
	columns := make([]interface{}, colLen)
	transf := make([]func(interface{}) string, colLen)

	for i := range columns {

		// transform the column value into a string or hex byte representation
		if IsBinaryType(colTypes[i]) {
			transf[i] = AsHexStr
			columns[i] = &sql.RawBytes{}
		} else {
			columns[i] = &sql.NullString{}
			transf[i] = AsStr
		}

	}

	// write the header line
	csvWriter.Write(cols)

	for rows.Next() {

		err = rows.Scan(columns...)
		if err != nil {
			return err
		}

		columnVals := make([]string, colLen)
		for i := range columns {
			columnVals[i] = transf[i](columns[i])
		}

		err = csvWriter.Write(columnVals)
		if err != nil {
			return err
		}
	}

	return nil

}

func IsBinaryType(t *sql.ColumnType) bool {

	//* BINARY
	//* VARBINARY
	//* BLOB
	//* TINYBLOB
	//* MEDIUMBLOB
	//* LONGBLOB

	n := strings.ToUpper(t.DatabaseTypeName())
	return strings.Contains(n, "BLOB") || strings.Contains(n, "BIN") || strings.Contains(n, "BYTE")
}

func AsStr(colVal interface{}) string {
	return colVal.(*sql.NullString).String
}

func AsHexStr(colVal interface{}) string {
	var bts []byte

	if colVal != nil {
		rawBytes := colVal.(*sql.RawBytes)
		bts = []byte(*rawBytes)
	}

	if len(bts) == 0 {
		return ""
	}

	return "\\x" + hex.EncodeToString(bts)
}
