package types

import (
	"io"
	io2 "pk/db/io"
	"pk/swagger"
)

type DBConnector interface {

	//return the char when typed means the command should be sent
	// in sql this is ';', in redis its '\n'
	EofChar() string

	Copy(db *swagger.Db, table string, csvCopyOptions *io2.CSVCopyOptions, reader *io.Reader) error

	Dump(db *swagger.Db, table string, csvDumpOptions *io2.CSVDumpOptions, writer io.Writer) error

	ListTables() ([]string, error)
}
