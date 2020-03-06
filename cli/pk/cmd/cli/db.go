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
	"errors"
	"fmt"
	"github.com/spf13/cobra"
	"io"
	"os"
	"os/exec"
	"pk/db"
	"pk/db/impl/usql"
	"pk/log"
	"pk/swagger"
	"strings"

	_ "github.com/xo/usql/drivers/mysql"
	_ "github.com/xo/usql/drivers/postgres"

)

// DbCmd represents the env command
var DbCmd = &cobra.Command{
	Use:   "db",
	Args:  cobra.ArbitraryArgs,
	Short: "Access a database as defined in the pkhub.io DBs",
	Long: `
Run a shell/repl for the database where you can interactively type in commands.

If you specify a file or input on stding this will be sent and executed on the database
and the pk will exit.
    `,
	Run: func(cmd *cobra.Command, args []string) {

		err := runDbCmd(cmd)

		if err != nil {
			ErrorAndExit(err)
		}

	},
}

func runDbCmd(cmd *cobra.Command) error {
	//download envs

	//download certificates
	dbDesc, dbConnector, err := GetDBConnection(cmd)

	if err != nil {
		return err
	}

	if dbDesc == nil {
		return errors.New("The DB Description cannot be nil here. Please check your db lbl is correct")
	}

	if dbConnector == nil {
		return errors.New("The DB Connector cannot be nil here. Please check your db lbl is correct")
	}

	// @TODO add support for copy to USQL Connector

	singleCommand, _ := cmd.Flags().GetString(CLI_DB_COMMAND_LBL.Long)
	outFile, _ := cmd.Flags().GetString(CLI_DB_OUT_LBL.Long)
	noAlign, _ := cmd.Flags().GetBool(CLI_DB_NO_ALIGN_LBL.Long)
	fieldSep, _ := cmd.Flags().GetString(CLI_DB_FIELD_SEP_LBL.Long)
	recordSep, _ := cmd.Flags().GetString(CLI_DB_RECORD_SEP_LBL.Long)
	tuplesOnly, _ := cmd.Flags().GetBool(CLI_DB_TUPLES_ONLY_LBL.Long)
	singleTx, _ := cmd.Flags().GetBool(CLI_DB_SINGLE_TX_LBL.Long)

	//No single command
	//Check for read from file command
	useStdIn, _ := cmd.Flags().GetBool(CLI_LBL_STDIN.Long)
	useFile, _ := cmd.Flags().GetString(CLI_LBL_FILE.Long)

	outputOptions := db.USQLOutputOptions{
		OutFile:         outFile,
		OutputMode:      getOutMode(cmd),
		NoAlign:         noAlign,
		FieldSeparator:  fieldSep,
		RecordSeparator: recordSep,
		TuplesOnly:      tuplesOnly,
		SingleTX:        singleTx,
	}
	var cmdReader io.Reader

	if singleCommand != "" {
		cmdReader = strings.NewReader(singleCommand)
	} else if useStdIn {

		//use stdin and execute statements and queries

		cmdReader = os.Stdin

	} else if useFile != "" {
		file, err := os.Open(useFile)

		if err != nil {
			return err
		}

		defer file.Close()

		cmdReader = file
	} else {
		cmdReader = nil
	}

	// Else we had no -c and no -i -f, we enter repl mode
	clientProg, _ := cmd.Flags().GetString(CLI_CLIENT_APP.Long)

	if clientProg != "" {
		log.Infof("Starting external cli %s.\n", clientProg)
		err = StartExternalProg(clientProg, *dbConnector)

		log.Debugf("Completed external prog %s.\n", clientProg)

	} else {
		//use repl
		log.Debugf("Read information from pkhub.\nStarting ReplOrCommands.\n")
		err = db.StartReplOrCommands(*dbDesc, *dbConnector, outputOptions, cmdReader)
		log.Debugf("Completed ReplOrCommands.\n")
	}

	if err == io.EOF {
		return nil
	}

	return err
}

func StartExternalProg(clientProg string, connector usql.USQLConnector) error {

	dbUrl := connector.ConnStr

	//osCmd := exec.Command(args[0], args[1:]...)
	osCmd := exec.Command(clientProg, dbUrl)

	return RunCommand(osCmd, true)
}

func GetDBConnection(cmd *cobra.Command) (*swagger.Db, *usql.USQLConnector, error) {

	safe, _ := cmd.Flags().GetString("safe")

	lblStr, _ := cmd.Flags().GetString("lbl")

	if safe == "" || lblStr == "" {
		return nil, nil, errors.New(fmt.Sprintf("safe and lbl are required parameters"))
	}

	sslMode, _ := cmd.Flags().GetString("ssl")

	dbs, _, err := get_dbs_from_api(safe, []string{lblStr})

	if err != nil {
		return nil, nil, err
	}

	if len(dbs) == 0 {
		return nil, nil, errors.New(fmt.Sprintf("No db was found for the name %s", lblStr))
	}

	dbConnector, err := usql.CreateConnector(dbs[0], sslMode)

	if err != nil {
		return nil, nil, err
	}

	return &dbs[0], dbConnector, nil
}

// Translate the output optiona flags like --csv --json --html into db.*_MODE options
func getOutMode(cmd *cobra.Command) int {
	//if ok, _ := cmd.Flags().GetBool(CLI_DB_HTML_LBL.Long); ok {
	//	return db.HTML_MODE
	//}
	if ok, _ := cmd.Flags().GetBool(CLI_DB_CSV_LBL.Long); ok {
		return db.CSV_MODE
	}

	if ok, _ := cmd.Flags().GetBool(CLI_DB_JSON_LBL.Long); ok {
		return db.JSON_MODE
	}

	return db.UNDEFINED_MODE
}

func init() {
	rootCmd.AddCommand(DbCmd)

	// Here you will define your flags and configuration settings.

	// Cobra supports Persistent Flags which will work for this command
	// and all subcommands, e.g.:
	// getCmd.PersistentFlags().String("foo", "", "A help for foo")

	// Cobra supports local flags which will only run when this command
	// is called directly, e.g.:
	DbCmd.Flags().StringP(CLI_DB_COMMAND_LBL.Long, CLI_DB_COMMAND_LBL.Short, "", "run a single command (SQL or internal) and exit")
	DbCmd.Flags().StringP(CLI_DB_OUT_LBL.Long, CLI_DB_OUT_LBL.Short, "", "output file")

	// @TODO This option has a bug that continuously prints empty strings to the output
	//DbCmd.Flags().BoolP(CLI_DB_HTML_LBL.Long, CLI_DB_HTML_LBL.Short, false, "HTML table output mode")


	DbCmd.Flags().BoolP(CLI_DB_CSV_LBL.Long, CLI_DB_CSV_LBL.Short, false, "CSV output mode")
	DbCmd.Flags().BoolP(CLI_DB_JSON_LBL.Long, CLI_DB_JSON_LBL.Short, false, "JSON output mode")

	//@TODO disable as not required now
	//DbCmd.Flags().BoolP(CLI_DB_NO_ALIGN_LBL.Long, CLI_DB_NO_ALIGN_LBL.Short, false, "unaligned table output mode")

	DbCmd.Flags().StringP(CLI_DB_FIELD_SEP_LBL.Long, CLI_DB_FIELD_SEP_LBL.Short, "|", "field separator for unaligned output (default, \"|\")")

	//@TODO tuples only does not work in this version
	//DbCmd.Flags().BoolP(CLI_DB_TUPLES_ONLY_LBL.Long, CLI_DB_TUPLES_ONLY_LBL.Short, false, "print rows only")

	DbCmd.Flags().BoolP(CLI_DB_SINGLE_TX_LBL.Long, CLI_DB_SINGLE_TX_LBL.Short, false, "execute as a single transaction (if non-interactive)")

	// @TODO add support for copy to USQL Connector

	DbCmd.Flags().StringP(CLI_CLIENT_APP.Long, CLI_CLIENT_APP.Short, "", "Use a client app like pgcli for interactive queries, default uses the internal repl. The command is run as [client] postgres://user:pass@host/db")

	DbCmd.PersistentFlags().StringP(CLI_LBL_SAFE.Long, CLI_LBL_SAFE.Short, "", "The safe name")

	DbCmd.PersistentFlags().StringP(CLI_LBL_LABEL.Long, CLI_LBL_LABEL.Short, "", "The database label name")

	DbCmd.PersistentFlags().BoolP(CLI_LBL_STDIN.Long, CLI_LBL_STDIN.Short, false, "Read data/sql from stdin")
	DbCmd.PersistentFlags().StringP(CLI_LBL_FILE.Long, CLI_LBL_FILE.Short, "", "Read data/sql from a file")

	DbCmd.PersistentFlags().StringP("ssl", "m", "disable", "Used for dbs that support sslmode disable,require,verify-ca,verify-full. Default is disable")

	DbCmd.MarkFlagRequired(CLI_LBL_SAFE.Long)
	DbCmd.MarkFlagRequired(CLI_LBL_LABEL.Long)

}
