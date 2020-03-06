package util

import (
	"bufio"
	"io"
	"strings"
)


//from https://stackoverflow.com/questions/33068644/how-a-scanner-can-be-implemented-with-a-custom-split
func SplitAt(substring string) func(data []byte, atEOF bool) (advance int, token []byte, err error) {

	return func(data []byte, atEOF bool) (advance int, token []byte, err error) {

		// Return nothing if at end of file and no data passed
		if atEOF && len(data) == 0 {
			return 0, nil, nil
		}

		// Find the index of the input of the separator substring
		if i := strings.Index(string(data), substring); i >= 0 {
			return i + len(substring), data[0:i], nil
		}

		// If at end of file with data return the data
		if atEOF {
			return len(data), data, nil
		}

		return
	}
}

func ScanStatements(reader io.Reader, split string, callback func(text string) error) error {
	scanner := bufio.NewScanner(reader)

	scanner.Split(SplitAt(split))
	for scanner.Scan() {

		text := scanner.Text()
		if strings.TrimSpace(text) == "" {
			continue
		}

		err := callback(text)
		if err != nil && err != io.EOF {
			return err
		}
	}
	return nil
}

// Use ReadString, scanner cannot handle long lines
func ScanLines(reader io.Reader, callback func(text string) error) error {

	var line string
	var err error

	buffReader := bufio.NewReader(reader)

	for {
		line, err = buffReader.ReadString('\n')

		if err != nil {
			break
		}

		if strings.TrimSpace(line) == "" {
			continue
		}

		err := callback(line)

		if err != nil {
			break;
		}
	}

	if err != io.EOF {
		return err
	}

	return nil
}

func Constantly(n int, v interface{}) []interface{} {
	seq := make([]interface{}, n)
	var i = 0

	for i < n {
		seq[i] = v
		i++
	}

	return seq
}

//Return a string of v repeated n times
func ConstantStr(n int, v string) string {
	var sb strings.Builder
	var i = 0
	for i < n {
		i++
		sb.WriteString(v)
	}

	return sb.String()
}
func ConvertStringToInterface(vals []string) []interface{} {
	rows := make([]interface{}, len(vals))

	for i := range vals {
		rows[i] = vals[i]
	}

	return rows
}


/*
   partition the input e.g
    input = [1 2 3 4 5 6 7]
    partition size=2
    output = [[1 2] [3 4] [5 6] [7]]
  */
func Partition(size int, input []string) [][]string{

	var divided [][]string

	for i := 0; i < len(input); i += size {
		end := i + size

		if end > len(input) {
			end = len(input)
		}

		divided = append(divided, input[i:end])
	}

	return divided
}
