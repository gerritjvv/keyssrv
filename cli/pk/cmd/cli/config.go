package cli

import (
	"fmt"
	"github.com/apex/log"
	"github.com/mitchellh/go-homedir"
	"gopkg.in/yaml.v2"
	"io/ioutil"
	"os"
)

type CliConfig struct {
	PKKeyId     string `yaml:"PK_KEY_ID"`
	PKKeySecret string `yaml:"PK_KEY_SECRET"`
	DefaultSafe string `yaml:"DEFAULT_SAFE"`
	DefaultName string `yaml:"DEFAULT_NAME"`
}

/*
 * Read the config from the file cfgFile, if empty try to read the default configuration file if present
 * Then reads the environment variables for each configuration param and if present override the values in the CliConfig struct.
 */
func InitConfig(cfgFile string) (*CliConfig, error) {

	config := CliConfig{}

	err := initWithFile(cfgFile, &config)

	if err != nil {
		return &config, err
	}

	err = initWithEnvVariables(&config)

	if err != nil {
		return &config, err
	}

	return &config, nil
}

// Override any config varaibles with their variables form the environment
// if any
func initWithEnvVariables(config *CliConfig) error {

	keyId := os.Getenv("PK_KEY_ID")
	if keyId != "" {
		config.PKKeyId = keyId
	}

	keySecret := os.Getenv("PK_KEY_SECRET")
	if keySecret != "" {
		config.PKKeySecret = keySecret
	}

	safe := os.Getenv("DEFAULT_SAFE")
	if safe != "" {
		config.DefaultSafe = safe
	}

	name := os.Getenv("DEFAULT_NAME")
	if name != "" {
		config.DefaultName = name
	}

	return nil
}




/**
 * Initiate the CliConfig fields from either the cfgFile or the default config file
 */
func initWithFile(cfgFile string, config *CliConfig) error {
	var yamlContents []byte

	if cfgFile != "" {
		// Use config file from the flag.

		log.Debugf("Reading config file: %s\n", cfgFile)
		bts, err := ioutil.ReadFile(cfgFile)
		if err != nil {
			return err
		}

		yamlContents = bts
	} else {

		//Use default pk.yaml file
		// Find home directory.
		home, err := homedir.Dir()
		if err != nil {
			return err
		}

		configFile := fmt.Sprintf("%s/.pk.yaml", home)

		// If a config file is found, read it in.
		if FileExists(configFile) {
			log.Debugf("Reading config file: %s\n", configFile)

			// Search config in home directory with name ".pk" (without extension).
			bts, err := ioutil.ReadFile(configFile)
			if err != nil {
				return err
			}

			yamlContents = bts
		}
	}


	if len(yamlContents) > 0 {
		err := yaml.Unmarshal(yamlContents, config)
		if err != nil {
			return err
		}
	}

	return nil

}

func FileExists(name string) bool {
	if _, err := os.Stat(name); err != nil {
		if os.IsNotExist(err) {
			return false
		}
	}
	return true
}
