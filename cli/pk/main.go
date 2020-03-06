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

package main

import (
	"pk/cmd/cli"

)

// set the build.sh by ld flags
// then passed to root.go which sets the root.go/Version which is used
// by the rest of the commands
var Version string

func main() {

	if Version == "" {
		panic("Please set the pk command version using ldflags")
	}

	cli.Execute(Version)
}
