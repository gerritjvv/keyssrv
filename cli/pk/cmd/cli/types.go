package cli

import "pk/swagger"

type EnvMount struct {
	Lbl string  // the environment label
	EnvVar string  // the environment variable name to write the mount's file to
	Env *swagger.Env
}