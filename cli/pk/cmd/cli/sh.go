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
	"github.com/spf13/cobra"
	"io"
	"math"
	"os"
	"os/exec"
	"os/signal"
	"pk/log"
	"pk/sh"
	"pk/swagger"
	"pk/util"
	"strings"
	"syscall"
)

//@TODO Add mount support
// This means we can mount environments to filesystem mounts using linux namespaces
//  How to do this in golang see https://www.infoq.com/articles/build-a-container-golang

// ShCmd represents the env command
var ShCmd = &cobra.Command{
	Use:   "sh",
	Args:  cobra.ArbitraryArgs,
	Short: "Run programs with named environments, or just bash pk sh --safe <safe> --lbls <env> -- bash",
	Long: `Run programs with environment variables populated
by envs setup as key value pairs.

Mounting files:
 To mount an environment variable as a file use the -e flag.
 
e.g
pk sh -s mysafe -n myenv1 -- bash
or
pk sh -s mysafe -e myconf1 -- bash
or
pk sh -s mysafe -n myenv1 -e myconf1 -- bash ssh -i $F_myconf1 $user@$host

    `,
	Run: func(cmd *cobra.Command, args []string) {

		lblsStr, _ := cmd.Flags().GetString("lbls")

		//download envs
		var err error
		var envs []swagger.Env

		if lblsStr != "" {
			envs, err = get_envs_from_lbls(cmd)

			if err != nil {
				ErrorAndExit(err)
			}
		}

		//download certificates

		safe, _ := cmd.Flags().GetString("safe")

		var osEnvVars []string

		//for each env set the os environment
		if len(envs) > 0 {
			//set environments to process
			for _, env := range envs {
				osEnvVars = parseEnvVars(osEnvVars, env)
			}
		}

		envMounts, _ := parseEnvMounts(cmd.Flags().GetStringSlice(CLI_LBL_MOUNT.Long))

		if err != nil {
			ErrorAndExit(err)
			return
		}

		if len(envMounts) > 0 {
			// Mount envs as files

			envMountEnvs, err := get_envs_from_mounts(safe, envMounts)

			if err != nil {
				ErrorAndExit(err)
			}

			store, err := sh.CreateStorage(getSizeInMb(envMountEnvs))

			if err != nil {
				ErrorAndExit(err)
				return
			}

			//add the mounts to storage
			osEnvVars, err = writeEnvsASFiles(store, osEnvVars, envMountEnvs)

			if err != nil {
				ErrorAndExit(err)
			}

			//cleanup the store on exit
			defer func() {
				log.Debugf("Deleting temp storage")
				err := store.Delete()
				if err != nil {
					log.Errorf("Error while deleting temp storage %s", err)
				} else {
					log.Debugf("Deleted temp storage")
				}
			}()

			// shutdown on OS signal
			c := make(chan os.Signal)
			signal.Notify(c,
				syscall.SIGHUP,
				syscall.SIGINT,
				syscall.SIGTERM,
				syscall.SIGQUIT)

			go func() {
				select {
				case _ = <-c:
					store.Delete()
					os.Exit(1)
				}
			}()

		}

		//osCmd := exec.Command(args[0], args[1:]...)
		osCmd := exec.Command("/bin/sh", "-c", strings.Join(args, " "))
		osCmd.Env = append(os.Environ(), osEnvVars...)

		it, err := cmd.Flags().GetBool("it")

		if err == nil {
			err = RunCommand(osCmd, it)
		}

		if err != nil {
			ErrorAndExit(err)
			return
		}
	},
}

func parseEnvMounts(envMountStrs []string, err error) ([]EnvMount, error) {

	if err != nil {
		return nil, err
	}

	envMounts := make([]EnvMount, len(envMountStrs))

	for i, s := range envMountStrs {

		if strings.Contains(s, ":") {
			splits := strings.Split(s, ":")

			envMounts[i] = EnvMount{
				Lbl: strings.TrimSpace(splits[0]),
				EnvVar: strings.TrimSpace(splits[1]),
			}

		} else {
			envMounts[i] = EnvMount{
				Lbl: s,
				EnvVar: fmt.Sprintf("F_%s", strings.ReplaceAll(s, "-", "_")),
			}
		}
	}

	return envMounts, nil
}

func getSizeInMb(envs []EnvMount) float64 {
	sizeInBts := 0 // we add another Kb to not fill the ram disk to capacity

	for _, env := range envs {
		if env.Env == nil {
			panic(fmt.Errorf("No environment was set for %s", env.Lbl))
		}

		sizeInBts += len([]byte(env.Env.Val))
	}

	return math.Max(0.512, float64(sizeInBts)/1024/1024)
}

// For each envMount we write the environment to a temp file name
// and add to osEnvVars ["F_$env_name=mount_point"]
func writeEnvsASFiles(store sh.Storage, osEnvVars []string, envMountEnvs []EnvMount) ([]string, error) {

	for _, env := range envMountEnvs {

		if env.Env == nil {
			return nil, fmt.Errorf("No environment was set for %s", env.Lbl)
		}

		fileName, err := store.StringAsFile(env.Lbl, env.Env.Val)

		if err != nil {
			return osEnvVars, err
		}

		log.Debugf("Write env %s as file %s and os env %s", env.Lbl, fileName, env.EnvVar)

		osEnvVars = append(osEnvVars, fmt.Sprintf("%s=%s", env.EnvVar, fileName))
	}

	return osEnvVars, nil
}

//Run command without terminal support
func RunCommand(cmd *exec.Cmd, tty bool) error {

	if tty {
		//if tty we make the current process' IO the command's IO
		cmd.Stdout = os.Stdout
		cmd.Stderr = os.Stderr
		cmd.Stdin = os.Stdin

	} else {
		//copy inputs
		stdIn, _ := cmd.StdinPipe()

		go func() {
			io.Copy(stdIn, os.Stdin)
		}()

		//write outputs
		stdErr, _ := cmd.StderrPipe()
		stdOut, _ := cmd.StdoutPipe()

		go func() {
			io.Copy(os.Stdout, stdOut)
		}()

		go func() {
			io.Copy(os.Stderr, stdErr)
		}()

	}

	err := cmd.Start()

	if err != nil {
		return err
	}

	return cmd.Wait()
}

func parseEnvVars(osEnvVars []string, env swagger.Env) []string {
	return append(osEnvVars, util.BuildVars(env.Val)...)
}

func init() {
	rootCmd.AddCommand(ShCmd)

	ShCmd.Flags().StringP(CLI_LBL_SAFE.Long, CLI_LBL_SAFE.Short, "", "The safe name")
	ShCmd.Flags().StringP(CLI_LBL_LABELS.Long, CLI_LBL_LABELS.Short, "", "Comma separated list of environment names/labels")

	ShCmd.Flags().BoolP("it", "i", false, "For tty terminal support")

	ShCmd.Flags().StringSliceP(CLI_LBL_MOUNT.Long, CLI_LBL_MOUNT.Short, []string{},
		`Write environments as files and make them available to sub processes.

Each environment is written to memory using a random file name. This file name is written
to an os environment variable using $F_[env-name] (all '-' chars are made '_').

Linux: we use mktempfs (cleanup is automatic by linux in case of process kill)
OSX: we use ramdisks (cleanup is via os signal trap, works with kill, No cleanup with kill -6 or -9)

On process exit all mounted environments are removed.

e.g pk sh -s mysafe -n myenv1 -v myenv2 -v myenv3:MY_CONF -- echo "FILE AT $F_myenv2 and $MY_CONF"
    
`)

	ShCmd.MarkFlagRequired(CLI_LBL_SAFE.Long)

}
