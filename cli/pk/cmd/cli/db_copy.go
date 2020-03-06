// Copyright © 2018 PKHub <admin@pkhub.io>
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package cli

import (
	"compress/bzip2"
	"compress/gzip"
	"errors"
	"fmt"
	"github.com/spf13/cobra"
	"io"
	"os"
	io2 "pk/db/io"
	"strings"
)

// DbCmd represents the env command
var DbCopyCmd = &cobra.Command{
	Use:   "copy",
	Args:  cobra.ArbitraryArgs,
	Short: "Copy data from a local file into a database table",
	Long: `Copy data from a local file into a database table.
By default assumes csv and reads the first line as the header columns line.
e.g 
a,b
1,2
3,4
Will be read as colum names ['a', 'b'] and records [1,2], [3,4]
    `,
	Run: func(cmd *cobra.Command, args []string) {

		err := runDbCopyCmd(cmd)

		if err != nil {
			ErrorAndExit(err)
		}

	},
}

func runDbCopyCmd(cmd *cobra.Command) error {
	var err error

	tableName, err := cmd.Flags().GetString("table")

	if err != nil || strings.TrimSpace(tableName) == "" || strings.Contains(tableName, ","){
		return fmt.Errorf("Please specify a valid table name, got \"%s\"", tableName)
	}

	dbDesc, dbConnector, err := GetDBConnection(cmd)

	if err != nil {
		return err
	}

	reader, closeable, err := openReader(cmd)

	if err != nil {
		return err
	}

	if reader == nil {
		return errors.New("Could not open input source [the reader cannot be nil here]")
	}

	defer closeable()


	sep, _ := cmd.Flags().GetString("sep")

	skipFirstLine, _ := cmd.Flags().GetBool("skip")
	header, _ := cmd.Flags().GetString("header")


	return (*dbConnector).Copy(dbDesc, tableName, &io2.CSVCopyOptions{Separator: sep, Header:header, SkipFirstLine:skipFirstLine}, &reader)
}



//open a reader either to a file or stdin.
//returns a closeable function to close the reader after use.
func openReader(cmd *cobra.Command) (io.Reader, func(), error) {
	useStdIn, _ := cmd.Flags().GetBool(CLI_LBL_STDIN.Long)
	useFile, _ := cmd.Flags().GetString(CLI_LBL_FILE.Long)


	if useStdIn {
		reader := os.Stdin
		return reader, func() {

		},
		nil

	} else if useFile != "" {
		readerF, err := openFile(useFile)
		if err != nil {
			return nil, nil, err
		}

		return readerF, func() {
			if readerF != nil {
				if closeable, ok := readerF.(io.Closer); ok {
					closeable.Close()
				}
			}
		}, nil

	} else {
		return nil, nil, errors.New("Please specify where to read the data from, either use i for standard input or f for file")
	}

}
func openFile(useFile string) (io.Reader, error) {

	file, err := os.Open(useFile)

	if err != nil {
		return nil, err
	}

	if strings.HasSuffix(useFile, ".gz") || strings.HasSuffix(useFile, ".gzip") {
		reader, err := gzip.NewReader(file)
		return reader, err
	}

	if strings.HasSuffix(useFile, ".bz2") || strings.HasSuffix(useFile, ".bzip2") {
		reader := bzip2.NewReader(file)
		return reader, nil
	}

	return file, err
}


func init() {
	DbCmd.AddCommand(DbCopyCmd)

	DbCopyCmd.Flags().StringP("table", "t",",", "The table to copy into")

	DbCopyCmd.Flags().StringP("sep", "c",",", "The character used to separate each field, default is ','")
	DbCopyCmd.Flags().StringP("header", "x", "", "The file header. Must align with the columns in the data file, use --skip/k if the data file contains a header line.")
	DbCopyCmd.Flags().StringP("fields-enclosed-by", "E", "", "For Mysql fields enclosed by.")

	DbCopyCmd.Flags().BoolP("skip", "k", false, "Skip the first line of the file, this is normally a header line.")
}
