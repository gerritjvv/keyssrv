package ssh

import (
	"fmt"
	"golang.org/x/crypto/ssh/agent"
	"net"
	"os"
	"pk/log"
	"pk/swagger"
)

const SSH_AUTH_SOCK  = "SSH_AUTH_SOCK"

// New returns a new agent.Agent that uses a unix socket
func New() (agent.Agent, net.Conn, error) {
	if !Available() {
		return nil, nil, fmt.Errorf("The SSH_AUTH_SOCK environment variable is not specified. Please ensure that ssh-agent is running on your system.")
	}

	sshAuthSock := os.Getenv(SSH_AUTH_SOCK)

	log.Debugf("Using %s %s", SSH_AUTH_SOCK, sshAuthSock)

	conn, err := net.Dial("unix", sshAuthSock)
	if err != nil {
		return nil, nil, fmt.Errorf("Error connecting to %s: %s", SSH_AUTH_SOCK, err)
	}

	return agent.NewClient(conn), conn, nil
}

// Available returns true is a auth socket is defined
func Available() bool {
	return os.Getenv(SSH_AUTH_SOCK) != ""
}

func AddKey(agentConn agent.Agent, cert swagger.Certificate, lifeTimeSecs int) (interface{}, error) {

	if cert.PrivKey == "" {
		return nil, fmt.Errorf("The key %s does not contain any private key value", cert.Lbl)
	}

	certBts, err := ReadPrivateKey(cert.Lbl, []byte(cert.PrivKey))

	if err != nil {
		return  nil, err
	}

	log.Debugf("Adding key %s with lifetime: %d", cert.Lbl, lifeTimeSecs)

	key := agent.AddedKey{PrivateKey: certBts, LifetimeSecs: uint32(lifeTimeSecs)}

	return key, agentConn.Add(key)
}