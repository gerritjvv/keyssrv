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

// getCmd represents the get command
var k8sCmd = &cobra.Command{
	Use:   "k8s",
	Long:`Kubernetes commands
`,
	Short: "kubernetes commands",
}

func init() {
	//k8sCmd.PersistentFlags().StringP("kubeconf", "c", "", "The safe that points to the kubernetes config. This the file contents KUBECONFIG will point to.")
	//k8sCmd.PersistentFlags().String("namespace", "", "The kubernetes namespace")
	rootCmd.AddCommand(k8sCmd)
}
