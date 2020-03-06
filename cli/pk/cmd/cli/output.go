package cli

import (
	"fmt"
	"github.com/savaki/jq"
	"os"
	"strings"
)

func ErrorAndExit(err error) {

	if err != nil {
		errStr := strings.ToLower(err.Error())

		if strings.Contains(errStr, "unauthorized") || strings.Contains(errStr, "401") {
			fmt.Fprintf(os.Stderr, "Unauthorized: Please check your cli credentials\n")
			fmt.Fprintf(os.Stderr, "For API key access: The environment variables PK_KEY_ID, PK_KEY_SECRET must contain valid API key values\n")
			fmt.Fprintf(os.Stderr, "log into pkhub.io and check that they haven't expired.\n")
			fmt.Fprintf(os.Stderr, "Or alternatively these can be defined in the ~/.pk.yaml file.\n")
			fmt.Fprintf(os.Stderr, "For direct user/password access, use the -p flag when running pk.\n")
			fmt.Fprintf(os.Stderr, "\n")
			fmt.Fprintf(os.Stderr, "To debug credentials, set the os environment variable: LOG_LEVEL=debug.\n")
			fmt.Fprintf(os.Stderr, "e.g\n")
			fmt.Fprintf(os.Stderr, "LOG_LEVEL=debug pk sh -s my-safe -n my-env -- bash\n")
		} else if strings.Contains(errStr, "404") {
			fmt.Fprintf(os.Stderr, "Safe or name/label not found\n")
			fmt.Fprintf(os.Stderr, "Please check that the safe provided exist (check upper-case lower-case chars and for '_' vs '-').\n")

		} else {
			fmt.Fprintf(os.Stderr, "Error running command %s\n", err)
		}

	} else {
		fmt.Fprintf(os.Stderr, "Error running command %s\n", err)
	}

	os.Exit(-1)
}

//Write string output to stdin
//if the --jq flag is specified we parse the string output through json first
func OutputSdtIn(output string) {
	if JQFilter != "" {
		op, err := jq.Parse(JQFilter)
		if err != nil {
			ErrorAndExit(err)
			return
		}
		value, err := op.Apply([]byte(output))

		if err != nil {
			ErrorAndExit(err)
			return
		}
		l := len(value)
		if l > 0 {

			if value[0] == '"' && value[l-1] == '"' {
				value = value[1 : l-1]
			}
		}
		fmt.Println(string(value))
	} else {
		fmt.Println(output)
	}
}
