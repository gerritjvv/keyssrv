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
	"compress/gzip"
	"fmt"
	"github.com/spf13/cobra"
	"io/ioutil"
	"os"
	"path/filepath"
	"pk/db/impl/usql"
	io2 "pk/db/io"
	"pk/log"
	"pk/swagger"
	"strings"
	"sync"
)

type DumpResult struct {

	FileNames []string
	Error error

}

// DbCmd represents the env command
var DBDumpCmd = &cobra.Command{
	Use:   "dump",
	Args:  cobra.ArbitraryArgs,
	Short: "Write/Dump data from a database into csv files",
	Long: `
Write/Dump data from a database into csv files.

The data can be imported using 'pk db copy' or any database utility
that can load csv data files into tables.

The assumption is that you manage your database schemas via a migration utility/tool,
and you only want to dump out the data in the tables.

Each table is written to a compressed gzip file.
    `,
	Run: func(cmd *cobra.Command, args []string) {

		err := runDBDumpCmd(cmd)

		if err != nil {
			ErrorAndExit(err)
		}

	},
}

func runDBDumpCmd(cmd *cobra.Command) error {
	var err error

	dbDesc, dbConnector, err := GetDBConnection(cmd)

	if err != nil {
		return err
	}

	// create dump options and either get a table from the params or from the db's list tables
	csvDumpOptions, err := createCSVDumpOptions(cmd, dbConnector)
	if err != nil {
		return err
	}

	if len(csvDumpOptions.Tables) == 0 {
		return fmt.Errorf("No tables found")
	}


	workDir, _ := cmd.Flags().GetString(CLI_DB_DIR.Long)

	if workDir == "" {
		workDir, err = os.Getwd()
		if err != nil {
			log.Errorf("Error getting pwd %s, using /tmp/", err)

			workDir = "/tmp/"
		}

		tempDir, err := ioutil.TempDir(workDir, fmt.Sprintf("%s_", dbDesc.Lbl))
		if err != nil {
			return err
		}

		workDir = tempDir
	}

	log.Infof("Using directory: %s", workDir)



	os.MkdirAll(workDir, os.ModeDir)
	if err != nil {
		return err
	}

	// partition tables into chunks
	parallism := 8


	//create a wgroup to sync on all chunked table dumps
	wgroup := sync.WaitGroup{}
	resultsChan := make(chan DumpResult)


	tableFeedChan := make(chan string)

	// for each tables chunk add to wgroup and run DumpTables
	for i := 0; i < parallism; i++ {

		wgroup.Add(1)

		go func(tableFeedChan <-chan string, resultsChan chan DumpResult) {
			defer wgroup.Done()
			// loop untill the tableFeedChan empty and closed
			for table := range tableFeedChan {
				resultsChan <- DumpTables(workDir, []string{table}, dbDesc, dbConnector, csvDumpOptions)
			}
		}(tableFeedChan, resultsChan)

	}

	// async wait for wgroup to complete and close resultsChan
	// we need to close resultsChan to end the below for loop
	go func() {
		wgroup.Wait()
		close(resultsChan)
	}()

	// write the tables to the tableFeedChan so that one of the parallel go routines can
	// pick it up and dump the table out
	go func(tables []string, tableFeedChan chan string) {
		defer close(tableFeedChan)

		for _, table := range tables {
			tableFeedChan <- table
		}

	}(csvDumpOptions.Tables, tableFeedChan)

	// will loop till wgroupWait returns and close(resultsChan) is run above
	// loop through all the DumpTables results and create tar file
	tableDumpCount := 0

	for dumpResult := range resultsChan {

		if dumpResult.Error != nil {
			return dumpResult.Error
		} else {
			tableDumpCount += len(dumpResult.FileNames)
			log.Debugf("Copied: %s\n", dumpResult.FileNames)
		}
	}

	log.Infof("Completed dumping all tables")
	log.Infof("Dumped %d tables to %s", tableDumpCount, workDir)

	return nil
}

// Write out table results to temp directory
func DumpTables(tablesDir string, tables []string,
	dbDesc *swagger.Db, dbConnector *usql.USQLConnector,
	options *io2.CSVDumpOptions) DumpResult {


	var fileNames []string

	for _, table := range tables {

		//create gzip file
		file, err := os.Create(filepath.FromSlash(tablesDir + "/" + table + ".dat.gz"))
		if err != nil {
			return DumpResult{Error: err}
		}

		log.Infof("[Begin] Dumping table %s", table)

		log.Debugf("[Begin] Writing table %s to file %s/%s", table, tablesDir, file.Name())

		writer := gzip.NewWriter(file)

		// dump out the table data
		err = dbConnector.Dump(dbDesc, table, options, writer)
		writer.Close()

		log.Debugf("[Complete] Writing table %s to file %s/%s", table, tablesDir, file.Name())

		if err != nil {
			log.Debugf("[ERROR] Writing table %s to file %s/%s => %s", table, tablesDir, file.Name(), err)
			return DumpResult{Error: err}
		}

		log.Infof("[Complete] Dumping table %s", table)

		fileNames = append(fileNames, file.Name())
	}

	return DumpResult{FileNames: fileNames}
}

func createCSVDumpOptions(cmd *cobra.Command, dbConnector *usql.USQLConnector) (*io2.CSVDumpOptions, error) {
	sep, _ := cmd.Flags().GetString(CLI_DB_FIELD_SEP_LBL.Long)

	if sep == "" {
		sep = ","
	}


	tablesStr, _ := cmd.Flags().GetString("tables")
	exclude, _ := cmd.Flags().GetString("exclude")

	var tables []string
	var err error

	//list all tables
	if tablesStr == "" {
		tables, err = listAllTables(dbConnector)

		if err != nil {
			return nil, err
		}

	} else {
		tables = strings.Split(tablesStr, ",")
	}


	tables = removeExcludes(tables, strings.Split(exclude, ","))

	return &io2.CSVDumpOptions{Separator: sep, Tables: tables}, nil
}

// remove all of the tables mentioned in excludes from tables,
// this operation is  case in-sensitive
func removeExcludes(tables []string, excludes []string) []string {

	if len(excludes) == 0 {
		return tables
	}

	excludeMap := make(map[string]bool)

	for _, exclude := range excludes {
		if strings.TrimSpace(exclude) != "" {
			excludeMap[strings.ToLower(exclude)] = true
		}
	}

	var tables2 []string

	for _, table := range tables {
		if !excludeMap[table] {
			tables2 = append(tables2, table)
		}
	}

	return tables2
}

func listAllTables(connector *usql.USQLConnector) ([]string, error) {
	return connector.ListTables()
}


func init() {
	DbCmd.AddCommand(DBDumpCmd)

	DBDumpCmd.Flags().StringP("tables", "t", "", "The tables to read from, if empty all tables in the public namespaces are included")
	DBDumpCmd.Flags().StringP("exclude", "x", "", "The tables to exclude")
	DBDumpCmd.Flags().StringP(CLI_DB_DIR.Long, CLI_DB_DIR.Short, "", "The directory to dump the table data to. If empty we use [current-dir]/[name]_[rand-number]")

	DBDumpCmd.Flags().StringP(CLI_DB_FIELD_SEP_LBL.Long, CLI_DB_FIELD_SEP_LBL.Short, ",", "The character used to separate each field, default is ','")


}
