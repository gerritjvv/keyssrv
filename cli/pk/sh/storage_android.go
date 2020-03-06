// +build android

package sh

import (
	"fmt"
	"runtime"
)

func CreateStorage(sizeMb float64) (Storage, error) {
	//
	//switch (runtime.GOOS) {
	//case "linux":
	//	return createLinuxStorage()
	//}

	return nil, fmt.Errorf("The OS %s is not support yet", runtime.GOOS)

}
