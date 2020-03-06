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
	"crypto/tls"
	"fmt"
	"github.com/abiosoft/readline"
	"net/http"
	"os"
	"pk/log"
	"strings"

	"github.com/spf13/cobra"
)

var cfgFile string
var insecure bool
var url string
var usePass bool //if true prompts for the user name and password

//
var Version string

// rootCmd represents the base command when called without any subcommands
var rootCmd = &cobra.Command{
	Use:   "pk",
	Short: "pkhub.io cli utility for developers",
	//Long: ``,

	// Uncomment the following line if your bare application
	// has an action associated with it:
	//	Run: func(cmd *cobra.Command, args []string) { },
	PersistentPreRun: func(cmd *cobra.Command, args []string) {

		cliConfig, err := InitConfig(cfgFile)

		if err != nil {
			fmt.Printf("Error trying to read configuration: %s\n", err)
		}

		PK_KEY_ID = cliConfig.PKKeyId
		PK_KEY_SECRET = cliConfig.PKKeySecret


		log.Debugf("Using PK_KEY_ID %s and PK_KEY_SECRET %s\n", obfuscate(PK_KEY_ID), obfuscate(PK_KEY_SECRET))

		switch cmd.Name() {
		case "version":
			return
		case "help":
			return
		}


		if usePass {
			userName, pass, err := promptPassword()
			if err != nil {
				fmt.Println(err)
				os.Exit(-1)
			}

			USER_NAME = strings.TrimSpace(userName)
			PASS = strings.TrimSpace(string(pass))

			if USER_NAME == "" || PASS == "" {
				fmt.Println("When using --p the user name and password must be provided")
				os.Exit(-1)
				return
			}
		}

		if !usePass && (PK_KEY_ID == "" || PK_KEY_SECRET == "") {
			fmt.Println("No authentication information provided")
			fmt.Println("Use the -p flag for User name and password authentication")
			fmt.Println("Or use the pkhub API keys and make them available using the PK_KEY_ID and PK_KEY_SECRET environment variables")
			os.Exit(-1)
			return
		}

		if insecure {
			//allow insecure when specified
			fmt.Fprintf(os.Stderr, "[WARN] Disabling TLS certificate verification\n")
			http.DefaultTransport.(*http.Transport).TLSClientConfig = &tls.Config{InsecureSkipVerify: true}
		}

	},
}

func promptPassword() (userName string, pass []byte, err error) {

	rl, err := readline.NewEx(&readline.Config{
		Prompt:                 "User name: ",
		DisableAutoSaveHistory: true,
	})

	if err != nil {
		panic(err)
	}

	defer rl.Close()

	userName, err = rl.Readline()

	if err != nil {
		return
	}

	pass, err = rl.ReadPassword("Password: ")

	if err != nil {
		return
	}

	return;
}

func obfuscate(str string) string {
	if len(str) > 6 {
		return str[len(str)-3:]
	}

	return ""
}

// Execute adds all child commands to the root command and sets flags appropriately.
// This is called by main.main(). It only needs to happen once to the rootCmd.
func Execute(version string) {

	Version = version

	setupLogging()

	if err := rootCmd.Execute(); err != nil {
		fmt.Println(err)
		os.Exit(1)
	}
}
func setupLogging() {

}

func init() {

	// Here you will define your flags and configuration settings.
	// Cobra supports persistent flags, which, if defined here,
	// will be global for your application.
	rootCmd.PersistentFlags().StringVar(&cfgFile, "config", "", "config file (default is $HOME/.pk.yaml)")
	rootCmd.PersistentFlags().BoolVar(&insecure, "insecure", false, "Only for localhost testing, this will turn off tls certification verification")
	rootCmd.PersistentFlags().StringVar(&url, "url", "https://pkhub.io", "Set the remote url default is pkhub.io")

	rootCmd.PersistentFlags().BoolVarP(&usePass, "p", "p", false, "Prompt for user name and password, for non interactive execution use the pk app keys")

	rootCmd.PersistentFlags().MarkHidden("insecure")
	rootCmd.PersistentFlags().MarkHidden("url")

	// Cobra also supports local flags, which will only run
	// when this action is called directly.
	//rootCmd.Flags().BoolP("toggle", "t", false, "Help message for toggle")

}
