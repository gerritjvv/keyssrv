package util

import (
	"pk/swagger"
	strings "strings"
)

//trim all strings in the list
func TrimAll(split []string) []string {
for i, v := range split {
split[i] = strings.TrimSpace(v)
}

return split
}

func CleanLine(s string) string {
	s2 := strings.TrimSuffix(s, "\r")
	s3 := strings.TrimSuffix(s2, "\n")

	return strings.TrimSpace(s3)
}

/**
 Return a map with key=val[N] that can be used as a hash set.
 */
func StringsAsSet(vals []string ) map[string]interface{} {

	m := make(map[string]interface{})

	for _, v := range vals{
		m[v] = v
	}

	return m
}

/**
Return a map with key=val[toLower(N.Lbl)] that can be used as a hash set.
*/
func CertsAsSet(certs []swagger.Certificate ) map[string]interface{} {

	m := make(map[string]interface{})

	for _, cert := range certs{
		v := strings.ToLower(cert.Lbl)
		m[v] = v
	}

	return m
}