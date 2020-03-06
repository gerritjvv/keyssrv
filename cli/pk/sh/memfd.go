// +build linux

package sh

import (
	"fmt"
	"golang.org/x/sys/unix"
	"io"
	"os"
	"sync"
)


type MemfdStorage struct {
	Lock sync.Mutex
	Files []io.Closer
}

func createLinuxStorage() (Storage, error) {
	return &MemfdStorage{}, nil
}


func (m MemfdStorage) StringAsFile(prefix string, v string) (string, error) {

	fd, err := memfile(prefix, []byte(v))

	if err != nil {
		return "", err
	}

	m.Lock.Lock()
	defer m.Lock.Unlock()

	// filepath to our newly created in-memory file descriptor
	fp := fmt.Sprintf("/proc/self/fd/%d", fd)

	file := os.NewFile(uintptr(fd), fp)

	m.Files = append(m.Files, file)

	return file.Name(), nil
}

func (m MemfdStorage) Delete() error {
	// delete is done automatically when all file descriptors are closed
	// but we delete early
	for _, file := range m.Files {
		_ = file.Close()
		// ignore error
	}
	return nil
}



// memfile takes a file name used, and the byte slice
// containing data the file should contain.
//
// name does not need to be unique, as it's used only
// for debugging purposes.
//
// It is up to the caller to close the returned descriptor.
func memfile(name string, b []byte) (int, error) {
	fd, err := unix.MemfdCreate(name, 0)
	if err != nil {
		return 0, fmt.Errorf("MemfdCreate: %v", err)
	}

	err = unix.Ftruncate(fd, int64(len(b)))
	if err != nil {
		return 0, fmt.Errorf("Ftruncate: %v", err)
	}

	data, err := unix.Mmap(fd, 0, len(b), unix.PROT_READ|unix.PROT_WRITE, unix.MAP_SHARED)
	if err != nil {
		return 0, fmt.Errorf("Mmap: %v", err)
	}

	copy(data, b)

	err = unix.Munmap(data)
	if err != nil {
		return 0, fmt.Errorf("Munmap: %v", err)
	}

	return fd, nil
}
