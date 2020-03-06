package cli

import (
	"encoding/base64"
	"fmt"
	"pk/swagger"
	"strings"
)

//set by the root command
var PK_KEY_ID string
var PK_KEY_SECRET string


var USER_NAME string
var PASS string

func AuthString() string {
	if USER_NAME != "" {
		return "BASIC: " + base64.StdEncoding.EncodeToString([]byte(USER_NAME + ":" + PASS))
	}

	return PK_KEY_ID + ":" + PK_KEY_SECRET
}

/**
Checks for a 401 message and returns a more helpful message
 */
func CheckUnAuthError(err error) error {

	if err != nil && strings.Contains(err.Error(), "401") {
		return fmt.Errorf(UnauthenticatedErrorString())
	}

	return err
}

func UnauthenticatedErrorString() string {
	return `
The Authentication information provided is not valid.

If you used API keys, please check the PK_KEY_ID PK_KEY_SECRET environment values, and make sure they are exported like "export PK_KEY_SECRET=val".

Debug help:
To see the last 3 characters of the api keys run the pk command with LOGLEVEL=debug.
    `
}

func SwaggerConfig() (*swagger.APIClient, *swagger.Configuration){


	cfg := swagger.NewConfiguration()

	client := swagger.NewAPIClient(cfg)

	cfg.UserAgent = "pk/go-client/0.1"
	cfg.BasePath = url

	return client, cfg
}
