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
	"github.com/spf13/cobra"
)

//Each sub command should add this as a flag when supported
// to allow for a better flag syntax experience.
// the output.go file reads this flag to see if any jq filter should be applied
var JQFilter string

// getCmd represents the get command
var getCmd = &cobra.Command{
	Use:   "get",
	Short: "Get \"safe\" resources",
	Long: `Return resource values for:

secrets   pk get secrets -safe <safe>  -lbls <labels>
logins    pk get logins -safe <safe>  -logins <login names>
certs     pk get certs -safe <safe>  -lbls <labels>
envs      pk get envs -safe <safe>  -lbls <labels>
snippets  pk get snippets -safe <safe>  -lbls <labels>

Where labels or logins are command separated strings
			
    `,

}

func init() {
	rootCmd.AddCommand(getCmd)
}
