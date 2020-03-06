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
	"pk/swagger"
	"strings"
	"time"
)

// getCmd represents the get command
var getEnvCmd = &cobra.Command{
	Use:   "env",
	Short: "Get environments for a safe",
	Run: func(cmd *cobra.Command, args []string) {
		err := get_envs(cmd)

		if err != nil {
			ErrorAndExit(err)
		}

	},
}

func get_envs(command *cobra.Command) error {

	envs, err := get_envs_from_lbls(command)

	if err != nil {
		return err
	}

	msg, _ := json.Marshal(envs)
	OutputSdtIn(string(msg))

	return nil
}

func get_envs_from_lbls(command *cobra.Command) ([]swagger.Env, error) {

	safe, err := command.Flags().GetString("safe")

	if err != nil {
		return nil, err
	}

	lblsStr, err := command.Flags().GetString("lbls")

	if err != nil {
		return nil, err
	}

	lbls := strings.Split(lblsStr, ",")

	if safe == "" || len(lbls) == 0 {
		return nil, errors.New(fmt.Sprintf("safe and lbls are required parameters"))
	}

	return get_envs_from_lbls2(safe, lbls)
}

// Add the Envs to each mount
func get_envs_from_mounts(safe string, mnts []EnvMount) ([]EnvMount, error) {

	mntsRet := make([]EnvMount, len(mnts))

	lbls := make([]string, len(mnts))

	for i, m := range mnts {
		lbls[i] = m.Lbl
	}
	
	envs, err := get_envs_from_lbls2(safe, lbls)

	if err != nil {
		return nil, err
	}

	for i, m := range mnts {

		env, ok  := getEnvForLbl(m.Lbl, envs)
		if !ok {
			return nil, fmt.Errorf("No environment found for %s", m.Lbl)
		}

		m.Env = env

		mntsRet[i] = m
	}

	return mntsRet, nil
}

func get_envs_from_lbls2(safe string, lbls []string) ([]swagger.Env, error) {
	client, _ := SwaggerConfig()

	var (
		ctx    context.Context
		cancel context.CancelFunc
	)

	ctx, cancel = context.WithTimeout(context.Background(), time.Second*60)

	defer cancel()

	envs, resp, err := client.ApiApi.ApiV1SafesEnvsGet(ctx, AuthString(), safe, lbls)

	if err != nil {
		return nil, CheckUnAuthError(err)
	}

	for _, lbl := range lbls {
		if !existLblForEnv(lbl, envs) {
			return nil, errors.New(fmt.Sprintf("No environment for %s", lbl))
		}
	}

	if resp.StatusCode > 399 {
		fmt.Fprintf(os.Stderr, "Error calling remote api: %d %s", resp.StatusCode, resp.Status)
	}

	return envs, nil
}

// Return true if eny of the envs have .Lbl == lbl
func existLblForEnv(lbl string, envs []swagger.Env) bool {

	for _, env := range envs {
		if env.Lbl == lbl {
			return true
		}
	}

	return false
}

func getEnvForLbl(lbl string, envs []swagger.Env) (*swagger.Env, bool) {
	for _, env := range envs {
		if env.Lbl == lbl {
			return &env, true
		}
	}

	return nil, false
}


func init() {
	getCmd.AddCommand(getEnvCmd)

	// Here you will define your flags and configuration settings.

	// Cobra supports Persistent Flags which will work for this command
	// and all subcommands, e.g.:
	// getCmd.PersistentFlags().String("foo", "", "A help for foo")

	// Cobra supports local flags which will only run when this command
	// is called directly, e.g.:
	getEnvCmd.Flags().StringP(CLI_LBL_SAFE.Long, CLI_LBL_SAFE.Short, "", "The safe name")
	getEnvCmd.Flags().StringP(CLI_LBL_LABELS.Long, CLI_LBL_LABELS.Short, "", "Comma separated list of environment labels")
	getEnvCmd.Flags().StringVarP(&JQFilter, CLI_LBL_JQ.Long, CLI_LBL_JQ.Short, "", "jq json filter")

	getEnvCmd.MarkFlagRequired(CLI_LBL_SAFE.Long)
	getEnvCmd.MarkFlagRequired(CLI_LBL_LABELS.Long)

}
