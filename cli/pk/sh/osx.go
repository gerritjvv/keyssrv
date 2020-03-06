package sh

import (
	"fmt"
	"io/ioutil"
	"pk/sh/suid"
)

type OSXStorage struct {
	RamDisk *suid.RamDisk
}

// Takes a string and write it as a file
// returns the file name generated
//// Takes a string and write it as a file
//// returns the file name generated
//StringAsFile(v string) (string, error)
//
//// Remove all storage resources mounts and files
//Delete() error

func (s OSXStorage) Delete() error {

	return suid.UmountAndDelete(s.RamDisk)
}


func (s OSXStorage) StringAsFile(prefix string, v string) (string, error)  {

	file, err := ioutil.TempFile(s.RamDisk.Mount, fmt.Sprintf("%s_*", prefix))

	if err != nil {
		return "", err
	}

	defer file.Close()

	_, err = file.WriteString(v)

	if err != nil {
		return "", err
	}

	return file.Name(), nil
}

func createOSXStorage(sizeMb float64) (*OSXStorage, error) {
	ramDisk, err :=suid.CreateAndMount(sizeMb)

	if err != nil {
		return nil, err
	}

	storage := OSXStorage{RamDisk: ramDisk}
	return &storage, nil
}