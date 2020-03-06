#!/usr/bin/env bash

DIR=$(cd -P -- "$(dirname -- "$0")" && pwd -P)

## Use artifactory gocenter.io for repository
GOPROXY=https://gocenter.io

set -e

CMD="$1"
if [ ! -z "$CMD" ];
then
 shift
fi

test () {

(cd $DIR && go test -v ./...)

}

get_latest_release () {
  curl --silent "https://api.github.com/repos/$1/releases/latest" | # Get latest release from GitHub api
    grep '"tag_name":' |                                            # Get tag line
    sed -E 's/.*"([^"]+)".*/\1/'                                    # Pluck JSON value
}


package () {


repo=pkhubio/pkcli

if [[ -z "$RELEASE_OVERRIDE" ]]; then


    RELEASE=`get_latest_release $repo`

    echo "GOT RELEASE: $RELEASE"

    if [[ -z "$RELEASE" ]] ; then

          sleep 2s
          curl  "https://api.github.com/repos/$repo/releases/latest"
          echo "Error, we cannot find the release string: got: $RELEASE"
          exit -1


    fi
else

RELEASE="$RELEASE_OVERRIDE"

fi

export VERSION=$("$DIR/../../scripts/increment-version.sh" -m $RELEASE)

echo $VERSION > "$DIR/resources/version.txt"

if [ ! "$VERSION" ];
then
echo "No version can be calculated, please check the above version download code"
exit -1
fi


echo "Build PK VERSION=$VERSION"

#echo CGO_ENABLED=0 go build -i -mod vendor
(cd $DIR && CGO_ENABLED=0 go build -v -ldflags="-s -w -X main.Version=$VERSION" -i -mod vendor)
}

run () {
 package
 "$DIR/pk" $@
}

SWAGGER_CMD="swagger-codegen"


unameOut="$(uname -s)"
case "${unameOut}" in
    Darwin*)
    SED_CMD="sed -i '' "
    ;;
    *)
    SED_CMD="sed -i "
    ;;
esac

check_swagger_installed () {

 set +e
 if command -v swagger-codegen ; then
   echo "Found swagger-codegen tool"
 else
   ls -la /usr/local/bin/swagger-codegen
   command -v swagger-codegen

   echo "swagger-codegen not installed"

   unameOut="$(uname -s)"

   echo "unameOut: $unameOut"

   case "${unameOut}" in
    Linux*)
    echo "Linux"

    ## download and build swagger
    (cd "/tmp/" && \
     wget http://central.maven.org/maven2/io/swagger/swagger-codegen-cli/2.3.1/swagger-codegen-cli-2.3.1.jar -O swagger-codegen-cli.jar)

    cat <<EOF >> /usr/bin/swagger-codegen
#!/usr/bin/env bash
java -jar /tmp/swagger-codegen-cli.jar \$@
exit 0
EOF
    chmod +x  /usr/bin/swagger-codegen
    ;;
    Darwin*)
    echo "installing via brew"
    brew install swagger-codegen
    ;;
    *)
    echo "Un supported architecture"
    exit -1
   esac
 fi
 set -e

 command -v swagger-codegen
 echo "Installed swagger-codegen"

}

gen_swagger () {

 check_swagger_installed


 if [ -f "$DIR/swagger.json" ];
 then
   echo "Using $DIR/swagger.json"
 else
   echo "Missing $DIR/swagger.json"
   exit -1
 fi

 mkdir -p "$DIR/swagger"

 (cd $DIR && \
  swagger-codegen generate -i "$DIR/swagger.json" -l go -o "$DIR/swagger" || echo "Error while generating swagger code")
    ## change "multi" for array params to "csv"

   find $DIR/swagger -name "*.go" -exec $SED_CMD 's;\"multi\";\"csv\";g' {} \;

}


check_go_installed () {

set +e

if  command -v go &> /dev/null ; then
  echo "go installed"
else
  echo "go not installed"
  set -e
  "$DIR"/goinstall.sh --64
fi

set -e

command -v go &> /dev/null

}

## enter a alpine_bash bash shell, with all the directories
## mounted that we requirer to build the pk cli
function alpine_bash {
  docker build -f "$DIR/Dockerfile-alpine" -t alpine_bash .

  SRC_DIR=$(cd -P -- "$(dirname -- "$DIR/../../../")" && pwd -P)

  docker run -it --rm -v $SRC_DIR:/app/keyssrv -w="/app/keyssrv/cli/pk"  alpine_bash
}


## enter a ubuntu bash shell, with all the directories
## mounted that we requirer to build the pk cli
function linux_bash {
  docker build -f "$DIR/Dockerfile-linux" -t linux_bash .

  SRC_DIR=$(cd -P -- "$(dirname -- "$DIR/../../../")" && pwd -P)

  docker run -it --rm -v $SRC_DIR:/app/keyssrv -w="/app/keyssrv/cli/pk"  linux_bash
}

function release_pk_cli {

 ## function that builds the pk-cli for linux, linux alpine, macos and windows
 ## different architectures

 #https://golang.org/doc/install/source#environment

 # GOOS GOARCH
 #android arm
 #darwin amd amd64
 #linux amd64
 #linux 386
 #linux arm
 #linux arm64

 # netbsd amd64
 # openbsd amd64
 # freebsd amd64

 #cannot run windows due to tty issue "windows,amd64",
 #windows amd64

 platforms=(
  "darwin,386",
  "darwin,amd64",
  "linux,386",
  "linux,amd64",
  "linux,arm",
  "linux,arm64",
  "windows,amd64",

  "openbsd,amd64",
  "netbsd,amd64"
  )

 for platform in ${platforms[@]}
 do
    unset platArch

    platArch=(${platform//,/ })


    export GOOS=${platArch[0]}
    export GOARCH=${platArch[1]}
    echo "GOOS=$GOOS  GOARCH=$GOARCH"

    go clean
    package
    RELEASE_DIR="$DIR/releases/$GOOS/$GOARCH"
    [[ -f $RELEASE_DIR ]] && rm -rf $RELEASE_DIR/pk
    mkdir -p $RELEASE_DIR

    echo "Using release dir: $RELEASE_DIR"
    mv $DIR/pk* $RELEASE_DIR/


 done
}


case "$CMD" in
    test )
    test
    ;;
	build )
	package
	;;
    run )
    run $@
    ;;
    swagger)
    gen_swagger
    ;;
    release )
    release_pk_cli $@
    ;;
    linuxbash )
    linux_bash
    ;;
    alpinebash )
    alpine_bash
    ;;
    * )
    echo "build|test|run|swagger|release"
    exit -1
    ;;
esac

exit 0

