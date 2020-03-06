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
	"fmt"
	"os"
	"path/filepath"
	"pk/swagger"
	"regexp"
	"strings"
	"time"

	"github.com/spf13/cobra"
	v1 "k8s.io/api/core/v1"
	"k8s.io/client-go/kubernetes"
	"k8s.io/client-go/kubernetes/scheme"
	"k8s.io/client-go/rest"
	_ "k8s.io/client-go/rest"
	"k8s.io/client-go/tools/clientcmd"
)

// getCmd represents the get command
var k8sApplySecretCmd = &cobra.Command{
	Use:   "secret",
	Short: "Creates a kubernetes secret from the environments specified",
	Long: `Creates a kubernetes secret from the environments specified
Each env can contain 1 or more yaml secret resources, the name and namespace
is specified in the yaml resource itself

E.g
apiVersion: v1
kind: Secret
metadata:
  name: ABC
  namespace: nabc
type: Opaque
stringData:
  username: mysecretname
---
Create the secret named "ABC" in the namespace "nabc"

    `,
	Run: func(cmd *cobra.Command, args []string) {

		loopMins, err := cmd.Flags().GetInt("loop")

		if err != nil {
			ErrorAndExit(err)
		}

		for {

			err = apply_secrets(cmd)

			if err != nil {
				fmt.Fprintf(os.Stderr, "Error running command %s\n", err)
			}

			if loopMins == 0 {
				return
			}

			time.Sleep(time.Duration(loopMins) * time.Minute)
		}

	},
}

func getKubeConfig () string {

	kubeConfig := os.Getenv("KUBECONFIG")
	if kubeConfig != "" {
		return kubeConfig
	}

	return filepath.Join(os.Getenv("HOME"), ".kube", "config")
}

func apply_secrets(command *cobra.Command) error {

	//safe, err := command.Flags().GetString("safe")

	envs, err := get_envs_from_lbls(command)

	if err != nil {
		return err
	}

	kubeconfig := getKubeConfig()

	var config *rest.Config

	if FileExists(kubeconfig) {
		config, err = clientcmd.BuildConfigFromFlags("", kubeconfig)

	} else {
		config, err = clientcmd.BuildConfigFromFlags("", "")
	}

	if err != nil {
		return err
	}

	client, err := kubernetes.NewForConfig(config)

	if err != nil {
		return err
	}

	if len(envs) > 0 {

		return applyEnvs(client, envs)
	}
	return nil
}

func applyEnvs(client kubernetes.Interface, envs []swagger.Env) error {
	var errRet error

	for _, env := range envs {
		err := applyEnv(client, env)
		if err != nil {
			errRet = err
		}
	}

	return errRet
}

//Take the environment, parses the yaml content
//and runs apply secret for each yaml document in the content found
func applyEnv(clientset kubernetes.Interface, env swagger.Env) error {

	var errRet error

	re := regexp.MustCompile("(?sm)^---$")

	for _, yamlDoc := range re.Split(env.Val, -1) {
		yamlDoc = strings.TrimSpace(yamlDoc)

		if yamlDoc == "" || yamlDoc == "\n" {
			continue
		}

		err := applyEnvYamlDoc(clientset, env.Lbl, yamlDoc)
		if err != nil {
			fmt.Printf("Error applying environment: %s\n", env.Lbl)
			errRet = err
		}
	}

	return errRet
}

//Takes a single yaml doc, parses the contents and creates a secret from it
func applyEnvYamlDoc(clientset kubernetes.Interface, envLbl string, yamlDoc string) error {
	decode := scheme.Codecs.UniversalDeserializer().Decode

	obj, _, err := decode([]byte(yamlDoc), nil, nil)

	if err != nil {
		return fmt.Errorf("Error creating secret from env %s : %s", envLbl, err.Error())
	}

	secret, ok := obj.(*v1.Secret)

	if !ok {
		return fmt.Errorf("Skipping non secret resource in env %s", envLbl)
	}

	namespace := secret.Namespace

	if namespace == "" {
		namespace = "default"
	}

	_, err = clientset.CoreV1().Secrets(namespace).Update(secret)

	if err != nil {
		// try to create the namespace if not found
		if strings.Contains(err.Error(), "not found") {
			_, err = clientset.CoreV1().Secrets(namespace).Create(secret)

			if err != nil {
				return err
			}
		} else {
			return err
		}
	}

	fmt.Printf("Created secret %s.%s from environment %s\n", namespace, secret.Name, envLbl)
	return nil
}

func init() {
	k8sApplyCmd.AddCommand(k8sApplySecretCmd)

	// Here you will define your flags and configuration settings.

	// Cobra supports Persistent Flags which will work for this command
	// and all subcommands, e.g.:
	// getCmd.PersistentFlags().String("foo", "", "A help for foo")

	// Cobra supports local flags which will only run when this command
	// is called directly, e.g.:
	k8sApplySecretCmd.Flags().StringP(CLI_LBL_SAFE.Long, CLI_LBL_SAFE.Short, "", "The safe name")
	k8sApplySecretCmd.Flags().StringP(CLI_LBL_LABELS.Long, CLI_LBL_LABELS.Short, "", "Comma separated list of secret labels, multiple labels will combine the environments")
	k8sApplySecretCmd.Flags().Int("loop", 0, "Run the same command continuously every N specified minutes")

	k8sApplySecretCmd.MarkFlagRequired(CLI_LBL_SAFE.Long)
	k8sApplySecretCmd.MarkFlagRequired(CLI_LBL_LABELS.Long)

}
