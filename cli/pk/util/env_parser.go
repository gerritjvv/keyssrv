package util

import (
	"bufio"
	"strings"
)


func BuildVars(txt string) []string {

	var vars []string

	scanner := bufio.NewScanner(strings.NewReader(txt))

	for scanner.Scan() {

		k, v := parseKeyVal(scanner.Text())

		if k == "" {
			continue
		}

		if strings.HasPrefix(v, "<<EOF") {
			v = strings.TrimPrefix(v, "<<EOF") + readTillEOF(scanner)
		}

		vars = append(vars, strings.TrimSpace(k) + "=" + strings.TrimSpace(v))
	}

	return vars
}
func parseKeyVal(text string) (string, string) {
	//Remove any export statements from the env
	textTrimmed := strings.TrimPrefix(strings.TrimSpace(text), "export")

	parts := strings.SplitN(textTrimmed, "=", 2)

	if len(parts) == 2 {
		return strings.TrimSpace(parts[0]), strings.TrimSpace(parts[1])
	}

	return "", ""
}

func readTillEOF(scanner *bufio.Scanner) string{

	var builder strings.Builder

	for scanner.Scan() {
		line := scanner.Text()

		if strings.TrimSpace(line) == "EOF" {
			break
		}

		builder.WriteString("\\n")
		builder.WriteString(line)
	}
	return builder.String()
}
