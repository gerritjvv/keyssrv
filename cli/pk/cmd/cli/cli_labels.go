/*
Contain the standard command labels
e.g label, safe etc
*/
package cli

type CliLabel struct {
	Long  string
	Short string
}

var CLI_LBL_SAFE = CliLabel{Long: "safe", Short: "s"}
var CLI_LBL_LABEL = CliLabel{Long: "lbl", Short: "n"}
var CLI_LBL_LABELS = CliLabel{Long: "lbls", Short: "n"}

var CLI_LBL_STDIN = CliLabel{Long: "stdin", Short: "i"}
var CLI_LBL_FILE = CliLabel{Long: "file", Short: "f"}

var CLI_LBL_JQ = CliLabel{Long: "jq", Short: "q"}

var CLI_LBL_MOUNT = CliLabel{Long: "volume", Short: "v"}

var CLI_DB_COMMAND_LBL = CliLabel{Long: "command", Short: "c"}
var CLI_DB_OUT_LBL = CliLabel{Long: "OUT", Short: "o"}
var CLI_DB_HTML_LBL = CliLabel{Long: "html", Short: "H"}
var CLI_DB_CSV_LBL = CliLabel{Long: "csv", Short: "C"}
var CLI_DB_JSON_LBL = CliLabel{Long: "json", Short: "J"}

var CLI_DB_NO_ALIGN_LBL = CliLabel{Long: "no-align", Short: "A"}
var CLI_DB_FIELD_SEP_LBL = CliLabel{Long: "field-separator", Short: "F"}
var CLI_DB_RECORD_SEP_LBL = CliLabel{Long: "record-separator", Short: "R"}
var CLI_DB_TUPLES_ONLY_LBL = CliLabel{Long: "tuples-only", Short: "t"}
var CLI_DB_SINGLE_TX_LBL = CliLabel{Long: "single-transaction", Short: "1"}

var CLI_DB_DIR = CliLabel{Long: "dir", Short: "d"}

var CLI_CLIENT_APP = CliLabel{Long: "client", Short: "x"}

var CLI_LBL_DOCKER = CliLabel{Long: "docker", Short: "d"}
var CLI_LBL_DOCKER_IMAGE = CliLabel{Long: "image", Short: "I"}
