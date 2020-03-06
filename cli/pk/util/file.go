package util

import (
	"os/user"
	"path/filepath"
	"strings"
)

/**
Supports ~ and *
Use like ~/* to get all directories and files from the user's home directory
*/
func ListFiles(path string) ([]string, error) {

	path2, err := ExpandPath(path)
	if err != nil {
		return nil, err
	}
	files, err := filepath.Glob(path2)

	if err != nil {
		return nil, err
	}

	names := make([]string, len(files))

	for _, f := range files {
		if strings.TrimSpace(f) != "" {
			names = append(names, f)
		}
	}

	return names, nil
}

func ExpandPath(path string) (string, error) {
	if len(path) == 0 || path[0] != '~' {
		return path, nil
	}

	usr, err := user.Current()
	if err != nil {
		return "", err
	}
	return filepath.Join(usr.HomeDir, path[1:]), nil
}
