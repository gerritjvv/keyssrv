#

# Cross compilation

To release run ```./build.sh release```

This will build for:

```
platforms=("darwin,386", "darwin,amd64", "linux,386", "linux,amd64", "linux,arm", "linux,arm64", "windows,amd64", "openbsd,amd64", "netbsd,amd64")
```

And write output to ```releases/platform/architecture```

# Linux Alpine support

See: 

https://stackoverflow.com/questions/36279253/go-compiled-binary-wont-run-in-an-alpine-docker-container-on-ubuntu-host

We're running with ```CGO_ENABLED=0``` so the ```linux amd64``` pk binary will work on alpine

# Install 

To install run the ./install.sh script

It has been tested on:

* Macos
* Ubuntu
* Debian
* Alpine
* ARM 64