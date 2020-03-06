package io

import (
	"io/ioutil"
	"strings"
)

// Function constructor - constructs new function for listing given directory
func listFiles(path string) func(string) []string {
	return func(line string) []string {
		var path2 string
		line2 := strings.TrimPrefix(strings.TrimSpace(line), "\\i")

		if line2 == "" {
			path2 = path
		}

		names := make([]string, 0)
		files, _ := ioutil.ReadDir(path2)
		for _, f := range files {
			names = append(names, f.Name())
		}

		return names
	}
}
