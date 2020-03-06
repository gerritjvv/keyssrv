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
	"fmt"
	"github.com/spf13/cobra"
	"io/ioutil"
	"net/http"
)

// helpCmd represents the help command
var updateCmd = &cobra.Command{
	Use:   "update",
	Short: "Checks that the current version is the latest and if not prints out update instructions",

	Run: func(cmd *cobra.Command, args []string) {
		latestVersion, err := GetLatestVersion()

		if err != nil {
			ErrorAndExit(err)
			return
		}

		if (latestVersion == Version) {
			fmt.Printf("You are on the latest version %s\n", Version)
		} else {
			fmt.Printf("Current version is %s lastest is %s\n", Version, latestVersion)
			fmt.Println("Please run the installer with: curl https://raw.githubusercontent.com/pkhubio/pkcli/master/install.sh | sh")
			fmt.Printf("or check https://github.com/pkhubio/pkcli/releases for %s\n", latestVersion)
		}
	},
}

type PKCLIGitRelease struct {
	TagName string `json:"tag_name"`
}

func GetLatestVersion() (string, error) {

	url := "https://api.github.com/repos/pkhubio/pkcli/releases/latest"

	resp, err := http.Get(url)

	if err != nil {
		return "", err
	}

	defer resp.Body.Close()
	body, err := ioutil.ReadAll(resp.Body)

	if err != nil {
		return "", err
	}

	release := PKCLIGitRelease{}
	err = json.Unmarshal(body, &release)

	if err != nil {
		return "", err
	}
	return release.TagName, nil
}

func init() {
	rootCmd.AddCommand(updateCmd)

	//Here you will define your flags and configuration settings.
	//
	//Cobra supports Persistent Flags which will work for this command
	//and all subcommands, e.g.:

	//Cobra supports local flags which will only run when this command
	//is called directly, e.g.:
	updateCmd.Flags().BoolP("toggle", "t", false, "")
}
