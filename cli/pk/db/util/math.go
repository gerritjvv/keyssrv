package util

// The largest length bigger than minVal in the slice
func MaxLengthStr(vals []string, minVal int) int {
	var l = minVal

	for _, v := range vals {
		l = Max(len(v), l)
	}

	return l
}

// The max val between a and b
func Max(a int, b int) int {
	if a > b {
		return a
	}

	return b
}
