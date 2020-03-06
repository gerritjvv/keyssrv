#!/usr/bin/env sh
#####
### Installs the latest pk cli app from github releases
####

set -e
echo "USING OS $OSTYPE"

GIT_USER=pkhubio
GIT_PROJECT=pkcli
BASE_URL=https://api.github.com/repos/$GIT_USER/$GIT_PROJECT/releases/latest

GOPROXY=https://gocenter.io
##https://github.com/$GIT_USER/$GIT_PROJECT/releases/latest

RELEASE=latest
BINARY=bin_file_on_release


check_deps () {
set +e
if command -v apk
then
 command -v apk
 command -v curl &> /dev/null || apk add curl
 command -v wget &> /dev/null || apk add wget
 command -v grep &> /dev/null || apk add grep
 export OS="linux"
fi

if command -v apt-get;
then
 command -v curl  || { apt-get update ; apt-get install -y curl; }
 command -v wget  || apt-get install -y wget
 command -v grep  || apt-get install -y grep
 export OS="linux"
fi

if command -v yum;
then
 command -v curl  || yum install -y curl
 command -v wget  || yum install -y wget
 command -v grep  || yum install -y grep
 export OS="linux"
fi
}

check_deps

set -e

if [ -z "$OS" ]; then
case "$OSTYPE" in
  darwin*)  OS="darwin" ;;
  linux*)   OS="linux"
            ;;
  msys*)    OS="windows" ;;
  *)        OS="linux"
            ;;
esac
fi

if [ "`getconf LONG_BIT`" -eq "64" ]
then
    ARCH="amd64"
else
    ARCH="386"
fi

echo "OS $OS ARCH $ARCH"
DOWNLOAD_URL=$(curl  $BASE_URL | grep "$OS-$ARCH" | grep -Eo '(https?|ftp|file)://[-A-Za-z0-9\+&@#/%?=~_|!:,.;]*[-A-Za-z0-9\+&@#/%=~_|]')


echo "Download URL: $DOWNLOAD_URL"

wget -O pk "$DOWNLOAD_URL"

if [ !  -d "/usr/local/bin/" ];
then
 echo "/usr/local/bin does not exist, creating"
 echo "Please place this directory in your PATH"
 mkdir -p /usr/local/bin
fi

chmod a+x pk
mv pk /usr/local/bin/
