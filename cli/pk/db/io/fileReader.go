package io

import (
	"encoding/csv"
	"io"
	"pk/util"
	"strings"
)

type CSVCopyOptions struct {
	SkipFirstLine bool
	Header        string
	Separator     string
	FieldsEnclosedBy string
}

type CSVDumpOptions struct {
	Separator     string
	Tables []string
}

/*
Read the header i.e column names:
 either from the first line of the file (default)
 or from the --header parameter
*/
func GetHeader(skipFirstLine bool, header string, reader func() ([]string, error)) ([]string, error) {

	var headers []string
	var err error

	if header != "" {
		headers = util.TrimAll(strings.Split(header, ","))

		//maybe skip the first line
		err = MaybeSkipFirstLine(skipFirstLine, reader)

	} else {
		//if no headers we assume the first line is the headers
		headers, err = reader()
	}

	if err != nil {
		return nil, err
	}

	//assume the record is the header
	return util.TrimAll(headers), nil
}

func MaybeSkipFirstLine(skipFirstLine bool, reader func() ([]string, error)) error {
	if skipFirstLine {
		//read first line, skip
		_, err := reader()
		return err
	}

	return nil
}

func OpenCSVReader(sep string, reader *io.Reader) *csv.Reader {

	csvReader := csv.NewReader(*reader)

	if strings.TrimSpace(sep) != "" {
		csvReader.Comma = []rune(sep)[0]
	}

	return csvReader
}
