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
	"encoding/json"
	"errors"
	"fmt"
	"github.com/spf13/cobra"
	"golang.org/x/net/context"
	"os"
	"strings"
	"time"
)

// getCmd represents the get command
var getSecretCmd = &cobra.Command{
	Use:   "secret",
	Short: "Get secrets for a safe",
	Run: func(cmd *cobra.Command, args []string) {
		err := get_secrets(cmd)

		if err != nil {
			ErrorAndExit(err)
		}

	},
}

func get_secrets(command *cobra.Command) error {


	safe, err := command.Flags().GetString("safe")

	if err != nil {
		return err
	}

	lblsStr, err := command.Flags().GetString("lbls")

	if err != nil {
		return err
	}

	lbls := strings.Split(lblsStr, ",")


	if safe == "" || len(lbls) == 0 {
		return errors.New(fmt.Sprintf("safe and lbls are required parameters"))
	}

	client, _ := SwaggerConfig()

	var (
		ctx    context.Context
		cancel context.CancelFunc
	)

	ctx, cancel = context.WithTimeout(context.Background(), time.Second * 60)

	defer cancel()

	secrets, resp, err := client.ApiApi.ApiV1SafesSecretGet(ctx, AuthString(), safe, lbls)

	if err != nil {
		return CheckUnAuthError(err)
	}

	if resp.StatusCode > 399 {
		fmt.Fprintf(os.Stderr, "Error calling remote api: %d %s", resp.StatusCode, resp.Status)
	}
	msg, _ := json.Marshal(secrets)
	OutputSdtIn(string(msg))

	return nil
}

func init() {
	getCmd.AddCommand(getSecretCmd)

	// Here you will define your flags and configuration settings.

	// Cobra supports Persistent Flags which will work for this command
	// and all subcommands, e.g.:
	// getCmd.PersistentFlags().String("foo", "", "A help for foo")

	// Cobra supports local flags which will only run when this command
	// is called directly, e.g.:
	getSecretCmd.Flags().StringP(CLI_LBL_SAFE.Long, CLI_LBL_SAFE.Short, "", "The safe name")
	getSecretCmd.Flags().StringP(CLI_LBL_LABELS.Long, CLI_LBL_LABELS.Short, "", "Comma separated list of secret labels")
	getSecretCmd.Flags().StringVarP(&JQFilter, CLI_LBL_JQ.Long, CLI_LBL_JQ.Short, "", "jq json filter")

	getSecretCmd.MarkFlagRequired(CLI_LBL_SAFE.Long)
	getSecretCmd.MarkFlagRequired(CLI_LBL_LABELS.Long)

}
