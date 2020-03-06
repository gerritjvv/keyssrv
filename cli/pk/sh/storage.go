package sh


type Storage interface {
	// Takes a string and write it as a file
	// returns the file name generated
	StringAsFile(prefix string, v string) (string, error)

	// Remove all storage resources mounts and files
	Delete() error
}


// Use the platform specific createStorage function
// see each file storage_[platform].go