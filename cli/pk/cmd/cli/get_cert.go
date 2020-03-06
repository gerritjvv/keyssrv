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
	"pk/swagger"
	"pk/util"
	"strings"
	"time"
)

// getCmd represents the get command
var getCertCmd = &cobra.Command{
	Use:   "cert",
	Short: "Get certificates for a safe",
	Run: func(cmd *cobra.Command, args []string) {
		err := get_certs(cmd)

		if err != nil {
			ErrorAndExit(err)
		}

	},
}

func get_certs(command *cobra.Command) error {


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

	certs, err := get_certs_from_lbls(safe, lbls)

	if err != nil {
		return err
	}

	msg, _ := json.Marshal(certs)
	OutputSdtIn(string(msg))

	return nil
}

func get_certs_from_lbls(safe string, lbls []string) ([]swagger.Certificate, error) {

	if len(lbls) == 0 {
		return []swagger.Certificate{}, nil
	}

	client, _ := SwaggerConfig()

	var (
		ctx    context.Context
		cancel context.CancelFunc
	)

	ctx, cancel = context.WithTimeout(context.Background(), time.Second * 60)

	defer cancel()

	certs, resp, err := client.ApiApi.ApiV1SafesCertsGet(ctx, AuthString(), safe, lbls)

	if err != nil {
		return nil, CheckUnAuthError(err)
	}

	if resp.StatusCode > 399 {
		return nil, fmt.Errorf("Error calling remote api: %d %s", resp.StatusCode, resp.Status)
	}


	if len(certs) == 0 {
		return nil, fmt.Errorf("No keys where found")
	}

	certsSet := util.CertsAsSet(certs)

	for _, lbl := range lbls {
		if _, ok := certsSet[strings.ToLower(lbl)]; !ok {
			return nil, fmt.Errorf("the key found for %s", lbl)
		}
	}

	return certs, nil
}

func init() {
	getCmd.AddCommand(getCertCmd)

	// Here you will define your flags and configuration settings.

	// Cobra supports Persistent Flags which will work for this command
	// and all subcommands, e.g.:
	// getCmd.PersistentFlags().String("foo", "", "A help for foo")

	// Cobra supports local flags which will only run when this command
	// is called directly, e.g.:
	getCertCmd.Flags().StringP(CLI_LBL_SAFE.Long, CLI_LBL_SAFE.Short, "", "The safe name")
	getCertCmd.Flags().StringP(CLI_LBL_LABELS.Long, CLI_LBL_LABELS.Short, "", "Comma separated list of certificate labels")

	getCertCmd.Flags().StringVarP(&JQFilter, CLI_LBL_JQ.Long, CLI_LBL_JQ.Short, "","jq json filter")

	getCertCmd.MarkFlagRequired(CLI_LBL_SAFE.Long)
	getCertCmd.MarkFlagRequired(CLI_LBL_LABELS.Long)

}
