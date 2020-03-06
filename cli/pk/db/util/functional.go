package util

// Return the first non empty string
func OrStr(args ...string) string {
	for _, arg := range args {
		if arg != "" {
			return arg
		}
	}

	return ""
}
