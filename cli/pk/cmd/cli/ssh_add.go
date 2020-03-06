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
	"crypto/ecdsa"
	"crypto/rsa"
	"fmt"
	"github.com/spf13/cobra"
	"pk/log"
	"pk/ssh"
	"strings"
)

//@TODO Add mount support
// This means we can mount environments to filesystem mounts using linux namespaces
//  How to do this in golang see https://www.infoq.com/articles/build-a-container-golang

// ShCmd represents the env command
var SshAdd = &cobra.Command{
	Use:   "ssh-add",
	Args:  cobra.ArbitraryArgs,
	Short: "Add your pub/priv keys to the system's ssh-agent for ssh, rsync, and scp",
	Long: `Add your pub/priv keys to the system's ssh-agent for ssh, rsync, and scp

Store your pub/priv keys for ssh into pkhub.
Then run ssh-add on any system to make your keys available to ssh, rsync and scp commands.

e.g
 pk ssh-add -s mysafe -n mykey

 ssh user1@server1

or multiple keys

 pk ssh-add -s mysafe -n mykey1,mykey2

To remove all identities from the ssh-agent, run ssh-add -D.

Debug: 
 - Please check that the ssh-agent is running on your system.
 - Check that the environment variable SSH_AUTH_SOCK is defined and pointing to the correct ssh-agent.
    `,
	Run: func(cmd *cobra.Command, args []string) {

		err := runSshAdd(cmd)

		if err != nil {
			ErrorAndExit(err)
			return
		}
	},
}


func runSshAdd(cmd *cobra.Command) error {
	var err error

	lblsStr, _ := cmd.Flags().GetString(CLI_LBL_LABELS.Long)
	safe, _ := cmd.Flags().GetString(CLI_LBL_SAFE.Long)

	lbls := strings.Split(lblsStr, ",")


	lifeTimeSecs, _ := cmd.Flags().GetInt("life-time")

	if safe == "" || len(lbls) == 0 {
		return fmt.Errorf("safe and lbls are required parameters")
	}

	certs, err := get_certs_from_lbls(safe, lbls)

	if err != nil {
		return err
	}

	agent, _, err := ssh.New()

	if err != nil {
		return err
	}

	for _, cert := range certs {
		k, err := ssh.AddKey(agent, cert, lifeTimeSecs)
		if err != nil {
			return err
		}
		log.Infof("Added %s key %s to ssh-agent", keyType(k), cert.Lbl)
	}

	return nil

}

func keyType(k interface{}) string{

	switch k.(type) {
	case rsa.PrivateKey: return "RSA"
	case ecdsa.PrivateKey: return "EC"
	}

	return "Private"
}

func init() {
	rootCmd.AddCommand(SshAdd)


	SshAdd.Flags().StringP(CLI_LBL_SAFE.Long, CLI_LBL_SAFE.Short, "", "The safe name")
	SshAdd.Flags().StringP(CLI_LBL_LABELS.Long, CLI_LBL_LABELS.Short, "", "Comma separated list of key name/labels")
	SshAdd.Flags().IntP("life-time", "t", 0, "The lifetime the key will be held by the ssh-agent. Default is 0." )

	SshAdd.MarkFlagRequired(CLI_LBL_SAFE.Long)
	SshAdd.MarkFlagRequired(CLI_LBL_LABELS.Long)
}
