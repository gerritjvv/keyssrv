package util

import (
	"github.com/xo/usql/text"
	"os"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
)

// See https://github.com/golang/go/issues/14626, when cross compiling the current user is not available
// because we cannot call gclib
type User struct {
	Uid      string // user id
	Gid      string // primary group id
	Username string
	Name     string
	HomeDir  string
}

func CurrentUser() *User {
	return &User{
		Uid:      strconv.Itoa(os.Getuid()),
		Gid:      strconv.Itoa(os.Getgid()),
		Username: OrStr(os.Getenv("USER"), os.Getenv("USERNAME")), // or USERNAME on Windows
		HomeDir:  UserHomeDir(), // or HOMEDRIVE+HOMEDIR on windows
	}
}

func UserHomeDir() string {
	if runtime.GOOS == "windows" {
		home := os.Getenv("HOMEDRIVE") + os.Getenv("HOMEPATH")
		if home == "" {
			home = os.Getenv("USERPROFILE")
		}
		return home
	} else if runtime.GOOS == "linux" {
		home := os.Getenv("XDG_CONFIG_HOME")
		if home != "" {
			return home
		}
	}

	return os.Getenv("HOME")
}

// HistoryFile returns the path to the history file.
//
// Defaults to ~/.<command name>_history, overridden by environment variable
// <COMMAND NAME>_HISTORY (ie, ~/.usql_history and USQL_HISTORY).
func HistoryFile(u *User) string {
	n := text.CommandUpper() + "_HISTORY"
	path := "~/." + strings.ToLower(n)
	if s := os.Getenv(n); s != "" {
		path = s
	}

	return expand(u, path)
}

// expand expands the tilde (~) in the front of a path to a the supplied
// directory.
func expand(u *User, path string) string {
	if path == "~" {
		return u.HomeDir
	} else if strings.HasPrefix(path, "~/") {
		return filepath.Join(u.HomeDir, strings.TrimPrefix(path, "~/"))
	}

	return path
}
