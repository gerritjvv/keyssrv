// +build darwin

package sh

import (
	"fmt"
	"runtime"
)

func CreateStorage(sizeMb float64) (Storage, error) {

	switch (runtime.GOOS) {
	case "darwin":
		return createOSXStorage(sizeMb)
	}

	return nil, fmt.Errorf("The OS %s is not support yet", runtime.GOOS)

}
