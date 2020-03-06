package suid

import (
	"crypto/rand"
	"encoding/base32"
	"errors"
	"fmt"
	"io/ioutil"
	"math"
	"os"
	"os/exec"
	"pk/log"
	"regexp"
	"strconv"
)

type RamDisk struct {
	Mount string
	FD string
}

var hdiutil = "hdiutil"

func CreatePinnedRamdisk(sizeMb float64) (disk string, err error) {
	// Creating an in-kernel ramdisk supposedly ensures that the memory
	// is pinned; see man 1 hdiutil.

	/*
	  RAMDISK_SECTORS=$((2048 * $RAMDISK_SIZE_MB))
	  DISK_ID=$(hdiutil attach -nomount ram://$RAMDISK_SECTORS)
	  echo "Disk ID is :" $DISK_ID
	 */
	ramdiskUri := fmt.Sprintf("ram://%d", int(math.Round(2048 * sizeMb)))


	log.Debugf("Creating ramdisk: %s", ramdiskUri)
	hdik := exec.Command( hdiutil, "attach", "-nomount", ramdiskUri)
	out, err := hdik.CombinedOutput()
	if err != nil {
		return "", fmt.Errorf("%s error: %s\n%s", hdiutil, err, out)
	}

	ramdiskre, _ := regexp.Compile("/dev/disk[0-9]+")
	ramdisk := ramdiskre.Find(out)
	if ramdisk == nil {
		return "", fmt.Errorf("%s didn't return a disk; output:\n %s", hdiutil, out)
	}
	return string(ramdisk), nil
}

func createFs(ramdisk string, uid int) (volname string, err error) {
	// Generate a random volume name.
	b := make([]byte, 20)
	_, err = rand.Read(b)
	if err != nil {
		return "", errors.New("couldn't generate random volume name")
	}
	volname = base32.StdEncoding.EncodeToString(b) + ".noindex"

	// Create the new filesystem
	// -P: set kHFSContentProtectionBit
	// -M 700: default mode=700
	// (newfs_hfs is relatively safe; it won't unmount and erase a mounted disk)
	newfs := exec.Command("/sbin/newfs_hfs", "-v", volname, "-U", strconv.Itoa(uid), "-G", "admin", "-M", "700", "-P", ramdisk)
	out, err := newfs.CombinedOutput()
	if err != nil {
		return "", fmt.Errorf("newfs_hfs error: %s", out)
	}

	return volname, nil
}

/*
    Run:
	umount -f $2
	hdiutil detach $2
 */
func UmountAndDelete(ramdisk *RamDisk)  error {
	cmd := exec.Command("umount", ramdisk.Mount)
	out, err := cmd.CombinedOutput()
	if err != nil {
		return fmt.Errorf("umount error: %s with umount %s", out, ramdisk.Mount)
	}

	cmd = exec.Command(hdiutil, "detach", ramdisk.FD)
	out, err = cmd.CombinedOutput()
	if err != nil {
		return fmt.Errorf("%s error: %s", hdiutil, out)
	}

	return nil
}

func CreateAndMount(sizeMb float64) (*RamDisk, error){

	uid := os.Getuid()

	// Check that the current directory is owned by the real uid.
	fi, err := os.Lstat(".")
	if err != nil {
		return nil, fmt.Errorf("Error statting current directory: %s", err)
	}
	if !fi.IsDir() {
		return nil, fmt.Errorf("Current directory not a directory.")
	}

	// Create the ramdisk
	ramdisk, err := CreatePinnedRamdisk(sizeMb)
	if err != nil {
		return nil, fmt.Errorf("createPinnedRamdisk: %s", err)
	}

	// Create the filesystem
	volname, err := createFs(ramdisk, uid)
	if err != nil {
		return nil, fmt.Errorf("createFs: %s", err)
	}

	// Create the mountpoint
	mountpoint, err := ioutil.TempDir(os.TempDir(), volname)

	if err != nil {
		return nil, err
	}

	err = os.Lchown(mountpoint, uid, -1)
	if err != nil {
		return nil, fmt.Errorf("couldn't chown directory: %s", err)
	}
	// Mount the new volume on the mountpoint
	mount := exec.Command("/sbin/mount_hfs", "-u", strconv.Itoa(uid), "-m", "700", "-o", "noatime,nosuid,nobrowse", ramdisk, string(mountpoint))
	out, err := mount.CombinedOutput()
	if err != nil {
		return nil, fmt.Errorf("couldn't mount new volume: %s\n%s", err, out)
	}
	log.Debugf("mount_hfs: %s", out)

	err = chprivDir(string(mountpoint), fmt.Sprintf("%d", uid))
	if err != nil {
		return nil, fmt.Errorf("coudn't make dir safe\n%s", err)
	}

	log.Debugf("Ramdisk: %s\n MountPoint: %s", ramdisk, mountpoint)

	return &RamDisk{Mount:mountpoint, FD:ramdisk}, nil
}