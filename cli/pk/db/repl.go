package db

import (
	_ "github.com/xo/usql/drivers"
	"github.com/xo/usql/env"
	_ "github.com/xo/usql/env"
	"github.com/xo/usql/handler"
	"github.com/xo/usql/rline"
	_ "github.com/xo/usql/rline"
	"github.com/xo/usql/text"
	_ "github.com/xo/usql/text"
	"pk/log"
	// add explicit support for cassandra
	_ "github.com/xo/usql/drivers/cassandra"
	// add explicit support for presto and athena
	_ "github.com/xo/usql/drivers/presto"

	"io"
	"os"
	"os/user"
	"pk/db/impl/usql"
	"pk/db/util"
	"pk/swagger"
)

const (
	JSON_MODE = iota
	HTML_MODE
	CSV_MODE
	UNDEFINED_MODE
)

/**
 * The the db.go file reads the command options create this struct
 * Then passed is to getPVariables which creates a array or key-value pairs that
 * are set in the repl as variable values.
 * These values are interpreted by usql to set its output options.
 *
 */
type USQLOutputOptions struct {
	//If emtpy stdout is used
	OutFile string
	//See JSON_MODE, HTML_MODE, CSV_MODE and UNDEFINED_MODE
	OutputMode int
	SingleTX   bool

	Expanded        bool
	TuplesOnly      bool
	RecordSeparator string
	FieldSeparator  string
	NoAlign         bool
}


//Translate the USQLOutputOptions into pset variables
func getPVariables(options USQLOutputOptions) [][]string {
	var vars = [][]string{}

	// translate output options to variable key values that can be set.
	//  set the format option to json html csv
	switch options.OutputMode {
	case JSON_MODE:
		vars = append(vars, []string{"format", "json"})
	case HTML_MODE:
		vars = append(vars, []string{"format", "html"})
	case CSV_MODE:
		vars = append(vars, []string{"format", "csv"})
	}

	if options.Expanded {
		vars = append(vars, []string{"expanded", "on"})
	}

	if options.TuplesOnly {
		vars = append(vars, []string{"tuples_only", "on"})
	}

	if options.RecordSeparator != "" {
		vars = append(vars, []string{"recordsep", options.RecordSeparator})
		vars = append(vars, []string{"recordsep_zero", "off"})
	}

	if options.FieldSeparator != "" {
		vars = append(vars, []string{"fieldsep", options.FieldSeparator})
		vars = append(vars, []string{"fieldsep_zero", "off"})
	}

	if options.NoAlign {
		vars = append(vars, []string{"format", "unaligned"})
	}

	return vars
}

// This method runs command both as a repl or if reader is not nill as non-interactive command executions
func StartReplOrCommands(swaggerDb swagger.Db, dbConnector usql.USQLConnector, options USQLOutputOptions, reader io.Reader) error {
	var err error

	// load current user
	cur := util.CurrentUser()

	// get working directory
	wd, err := os.Getwd()
	if err != nil {
		return err
	}

	// create input/output
	// if we have a io record we are not entering repl mode but executing commands
	l, err := rline.New(reader != nil, options.OutFile, util.HistoryFile(cur))
	if err != nil {
		return err
	}

	defer l.Close()

	// important to set this, otherwise on each query the host connect info is printed out
	env.Set("SHOW_HOST_INFORMATION", "false")

	//Set variables that the connector defines to support specific db connector vars
	for _, replVar := range dbConnector.ReplVars {
		if err = env.Set(replVar[0], replVar[1]); err != nil {
			return err
		}
	}

	//Set the output and format variables as extracted from the options
	for _, replVar := range getPVariables(options) {
		if len(replVar) == 1 {
			if _, err = env.Ptoggle(replVar[0], ""); err != nil {
				return err
			}
		} else {
				if _, err = env.Pset(replVar[0], replVar[1]); err != nil {
				return err
			}
		}
	}

	// create handler
	h := handler.New(l, convertToUsqlUser(cur), wd, false)

	dsn := dbConnector.ConnStr

	log.Debugf("Open connection to db: %s %s:%s %s\n", swaggerDb.Dbuser, swaggerDb.Host, swaggerDb.Port, swaggerDb.Database)

	// open dsn
	if err = h.Open(dsn); err != nil {
		log.Debugf("Error opening connection to db\n")

		return err
	}

	log.Debugf("Connection to db opened\n")


	if options.SingleTX {
		log.Debugf("Single TX begin\n")

		if h.IO().Interactive() {
			return text.ErrSingleTransactionCannotBeUsedWithInteractiveMode
		}
		if err = h.Begin(); err != nil {
			return err
		}
	}

	f := h.Run

	// setup runner
	if reader != nil {
		f = func() error {
			// force single line mode, we are executing command by command
			h.SetSingleLineMode(true)

			return util.ScanStatements(reader, dbConnector.EofChar(), func(text string) error {

				// this is USQL specific, it sets the query buffer for the Handler to text
				// then runs the query/command
				h.Reset([]rune(text))
				if err := h.Run(); err != nil && err != io.EOF {
					return err
				}

				return nil
			})
		}
	}

	// run
	if err = f(); err != nil {
		return err
	}

	if options.SingleTX {
		log.Debugf("Single TX commit\n")

		return h.Commit()
	}

	return nil

}

// Conversion util to convert from our user struct to a usql one
func convertToUsqlUser(cur *util.User) *user.User {
	return &user.User{
		Name:     cur.Name,
		Gid:      cur.Gid,
		HomeDir:  cur.HomeDir,
		Uid:      cur.Uid,
		Username: cur.Username,
	}
}
