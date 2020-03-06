/**
  Run commands as docker images, defaults to ubuntu latest

  See https://docs.docker.com/develop/sdk/examples/
 */
package docker

import (
	"github.com/docker/docker/api/types"
	"github.com/docker/docker/api/types/container"
	"github.com/docker/docker/client"
	"golang.org/x/net/context"
	"io"
	"os"
)

type ContainerExecRet struct {
	status int64
	err error
}


type DockerMount struct {
	HostPath   string
	DockerPath string
}


/*
 Runs a command in docker ubuntu
 Returns the command return status and any errors
 */
func Run(image string, cmd []string, tty bool, envs []string, mounts []DockerMount) (int64, error){
	ctx := context.Background()
	cli, err := client.NewEnvClient()
	if err != nil {
		return 0, err
	}

	reader, err := cli.ImagePull(ctx, "docker.io/library/ubuntu", types.ImagePullOptions{})
	if err != nil {
		return 0, err
	}

	io.Copy(os.Stdout, reader)

	resp, err := cli.ContainerCreate(ctx, &container.Config{
		Image: image,
		Cmd:   cmd,
		Tty:   tty,
		AttachStdout:true,
		AttachStdin: true,
		AttachStderr: true,
	}, nil, nil, "")


	if err != nil {
		return 0, err
	}

	if err := cli.ContainerStart(ctx, resp.ID, types.ContainerStartOptions{}); err != nil {
		panic(err)
	}

	execCh := make(chan ContainerExecRet)
	logTailCh := make(chan ContainerExecRet)

	go func() {
		status, err := cli.ContainerWait(ctx, resp.ID)

		if err != nil {
			execCh <- ContainerExecRet{0, err}
		}

		execCh <- ContainerExecRet{status, nil}
	}()




	go func() {
		defer func() {logTailCh <- ContainerExecRet{0, nil}}()

		out, err := cli.ContainerLogs(ctx, resp.ID, types.ContainerLogsOptions{ShowStdout: true, Follow:true, Tail: "0", })
		if err != nil {
			logTailCh <- ContainerExecRet{0, err}
		}

		io.Copy(os.Stdout, out)

	}()

	containerExecRet := <-execCh
	<-logTailCh

	return containerExecRet.status,containerExecRet.err

}
//
//func createExecConfig(env []string, cmd []string) types.ExecConfig {
//
//	return types.ExecConfig{
//		User: "root",
//		AttachStderr: true,
//		AttachStdin: true,
//		AttachStdout: true,
//		Privileged: true,
//		Tty: true,
//
//		Env: env,
//		Cmd: cmd,
//	}
//}