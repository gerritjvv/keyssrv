package ssh

import (
	"crypto/x509"
	"encoding/pem"
	"fmt"
	"os"
	"pk/log"
	"strings"

	"github.com/howeyc/gopass"
)

const (
	// PrivateKey is the "PRIVATE KEY" block type.
	PrivateKey string = "PRIVATE KEY"

	OpenSshPrivateKey string = "OPENSSH PRIVATE KEY"

	// RSAPrivateKey is the "RSA PRIVATE KEY" block type.
	RSAPrivateKey string = "RSA PRIVATE KEY"

	// ECPrivateKey is the "EC PRIVATE KEY" block type.
	ECPrivateKey string = "EC PRIVATE KEY"

	// PublicKey is the "PUBLIC KEY" block type.
	PublicKey string = "PUBLIC KEY"

	// Certificate is the "CERTIFICATE" block type.
	Certificate string = "CERTIFICATE"
)

// See https://github.com/shazow/ssh-chat/blob/master/cmd/ssh-chat/key.go#L17
// ReadPrivateKey attempts to read your private key and possibly decrypt it if it
// requires a passphrase.
// This function will prompt for a passphrase on STDIN if the environment variable (`IDENTITY_PASSPHRASE`),
// is not set.
//  return one of rsa.PrivateKey, dsa.PrivateKey, ecdsa.PrivateKey, ed25519.PrivateKey
func ReadPrivateKey(lbl string, privateKey []byte) (interface{}, error) {

	block, rest := pem.Decode(privateKey)
	if len(rest) > 0 {
		return nil, fmt.Errorf("Your private key is not in the expected pem format. Error: extra data when decoding private key")
	}

	//if not encrypted return the pem key
	if !x509.IsEncryptedPEMBlock(block) {
		log.Debugf("Parsing un-encrypted private key")
		return ParsePrivateKey(block.Type, block.Bytes)
	}

	return readPasswordProtectedKey(lbl, block)
}

func readPasswordProtectedKey(lbl string, block *pem.Block) (interface{}, error) {
	var err error

	log.Debugf("Parsing encrypted private key")

	passphrase := []byte(os.Getenv("ID_PASSWD"))

	// -------------- prompt for pass phrase
	if len(passphrase) == 0 {
		log.Debugf("No value for password in env variable ID_PASSWD, prompting for password")

		fmt.Printf("Enter key %s passphrase: ", lbl)

		passphrase, err = gopass.GetPasswd()
		if err != nil {
			return nil, fmt.Errorf("couldn't read passphrase: %s", err)
		}
	}

	// -------------- decrypt key
	der, err := x509.DecryptPEMBlock(block, passphrase)
	if err != nil {
		return nil, fmt.Errorf("decrypt failed for key %s: %s", lbl, err)
	}

	//parse based on block type
	return ParsePrivateKey(block.Type, der)
}

// Parse RSA or ECP keys
func ParsePrivateKey(blockType string, bts []byte) (interface{}, error) {

	switch strings.ToUpper(blockType) {
	case PrivateKey:
		return parsePKCSPrivateKey(bts)
	case RSAPrivateKey:
		return parsePKCSPrivateKey(bts)
	case OpenSshPrivateKey:
		return parsePKCSPrivateKey(bts)
	case ECPrivateKey:
		return x509.ParseECPrivateKey(bts)
	case Certificate:
		return x509.ParseCertificate(bts)
	case PublicKey:
		return x509.ParsePKIXPublicKey(bts)
	}

	//as a last resort try parsing as rsa key
	k, err := parsePKCSPrivateKey(bts)
	if err != nil {
		return nil, fmt.Errorf("The key type %s is not supported. Please use a pem encoded RSA or ECP key", blockType)
	}

	return k, nil

}

// ParsePKCSPrivateKey attempts to decode a RSA private key first using PKCS1
// encoding, and then PKCS8 encoding.
func parsePKCSPrivateKey(buf []byte) (interface{}, error) {
	// attempt PKCS1 parsing
	key, err := x509.ParsePKCS1PrivateKey(buf)
	if err == nil {
		return key, nil
	}

	// attempt PKCS8 parsing
	return x509.ParsePKCS8PrivateKey(buf)
}
