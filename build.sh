#!/usr/bin/env bash
# Faster docker with nfs https://medium.com/@sean.handley/how-to-set-up-docker-for-mac-with-native-nfs-145151458adc

if [ -z "$AWS_PROFILE" ]; then
  echo "1"
AWS_PROFILE=vaultbuild
fi

if [ -z "$BITBUCKET" ]; then
 if [ -z "$AWS_PROFILE" ]; then
  echo "3"
  export AWS_PROFILE=vaultbuild
 fi
fi

#echo "USING AWS Profile $AWS_PROFILE"

export DOCKER_REGISTRY_URL="351415477200.dkr.ecr.eu-west-1.amazonaws.com"
export CACHE_IMAGE="$DOCKER_REGISTRY_URL/keyssrv-dev:latest"


if ! [ -z "$(command -v drip)" ]; then
 LEIN_JAVA_CMD=$(command -v drip)
 echo "Using lein drip"
fi

set -e

DIR=$(cd -P -- "$(dirname -- "$0")" && pwd -P)


# export for docker-compose nfs setup
export SOURCE_DIR="$DIR"


function ecr_login {

set +e

AWS_CMD=$(command -v aws)


echo "AWS cli command " $(command -v aws)

eval $($AWS_CMD ecr get-login --no-include-email --region eu-west-1 || $AWS_CMD ecr get-login --region eu-west-1)

set -e

}

function deploy_base_images {

ecr_login

(cd $DIR && \
     docker build -f Dockerfile-dev-base -t keyssrv-dev-base . && \
     docker tag keyssrv-dev-base:latest $DOCKER_REGISTRY_URL/keyssrv-dev-base:latest && \
     docker push $DOCKER_REGISTRY_URL/keyssrv-dev-base:latest)

}

function package_cli {
(cd "$DIR/cli/pk" && \
  ./build.sh test && \
  ./build.sh build )
}

function check_main_package () {

 (cd "$DIR" && \
  rm -f target/resources && \
  cp ./entry-point.sh target/ && \
  cd  target && \
  ln -s ../resources . && \
  ./entry-point.sh test)
}


function package {
 set -e
 cd "$DIR"

 lein do clean, javac
 echo ">>>>>>>>>>>>>>>>>>>>>>>>>> COMPILE >>>>>>>>>>>>>>>>>>>>>>>>>"
 lein compile
 echo ">>>>>>>>>>>>>>>>>>>>>>>>>> CHECK >>>>>>>>>>>>>>>>>>>>>>>>>"
 lein check
 echo ">>>>>>>>>>>>>>>>>>>>>>>>>> JAR >>>>>>>>>>>>>>>>>>>>>>>>>"

 lein with-profile jar jar

 echo "Check main packkage"
 # we must check that the application can run with a test run after compile
 check_main_package

}


function run_compose {
    echo "run docker-compose $DIR/docker-compose.yml $@"
    docker-compose -f "$DIR/docker-compose.yml" $@
}

function check_compose {
    set +e
    if ! command -v docker-compose &> /dev/null ; then
        echo "Docker compose is not installed"
        exit -1;
    fi

    set -e
}


function check_demo_keys_exist {

#echo "Creating keys in $DIR/.data/keys"

 if [ ! -f "$DIR/.data/keys/key.pem" ]; then


#see https://stackoverflow.com/questions/10175812/how-to-create-a-self-signed-certificate-with-openssl
# answer "2017 One liner:"

echo "Creating keys in $DIR/.data/keys"
     mkdir -p "$DIR/.data/keys"

     CNF="$DIR/.data/keys/ssl.cnf"
     if [ -f "/System/Library/OpenSSL/openssl.cnf" ]; then
         echo "using macosx openssl.cnf"
         CNF="/System/Library/OpenSSL/openssl.cnf"
     else
         cat >$CNF <<EOF
[req]
distinguished_name = req_distinguished_name
x509_extensions = v3_req
prompt = no
[req_distinguished_name]
C = US
ST = na
L = na
O = mykeyssrv
OU = ensec
CN = www.mykeyssrv.com
[v3_req]
keyUsage = critical, digitalSignature, keyAgreement
extendedKeyUsage = serverAuth
subjectAltName = @alt_names
[alt_names]
DNS.1 = mykeyssrv
DNS.2 = mykeyssrv

EOF
     fi

  (cd "$DIR/.data/keys/" && \
       openssl req -x509 -newkey rsa:2048 -nodes \
               -keyout key.pem -out cert.pem \
               -subj /CN=hydra \
               -reqexts SAN \
               -extensions SAN \
               -config <(cat $CNF \
                       <(printf '[SAN]\nsubjectAltName=DNS:mykeyssrv')) \
               -sha256 \
               -days 3560)

   mkdir -p "$DIR/.data/certs" && \
   cp "$DIR/.data/keys/cert.pem" "$DIR/.data/certs/mykeyssrv.crt"


  fi

  if [ -z "$KEY_PEM_PATH" ]; then
    export KEY_PEM_PATH="$DIR/.data/keys/key.pem"
  fi
  if [ -z "$CERT_PEM_PATH" ]; then
    export CERT_PEM_PATH="$DIR/.data/keys/cert.pem"
  fi

}

function start_components {

    package
    run_compose build && \
    run_compose up -d postgresd && \
    sleep 3s && \
    keyssrv_migrate
#    run_compose up -d keyssrv && \
#    sleep 3s && \
#    run_compose logs keyssrv
}

function keyssrv_logs {
    run_compose logs -f keyssrv
}

function keyssrv_migrate {
    run_compose run --rm keyssrv-dev lein run migrate
}

#function restart_keyssrv {
#
#    run_compose stop keyssrv
#    package
#    run_compose up -d keyssrv
#
#}

function run_dev_bash {
    (cd $DIR && \
    lein libdir && \
#    run_compose build keyssrv-dev && \
    run_compose build && \
    run_compose up -d postgresd && \
    run_compose up -d mysql && \
    sleep 3s && \
    keyssrv_migrate

    run_compose run --service-ports --rm keyssrv-dev bash)
}

function run_dev_test {

    (cd $DIR && \
    lein run migrate && \
    lein test
    )
}

## the docker image for development and ./build bash
function deploy_dev_docker {

ecr_login

(cd $DIR && \
     docker build -f Dockerfile-dev -t keyssrv-dev . && \
     docker tag keyssrv-dev:latest $DOCKER_REGISTRY_URL/keyssrv-dev:latest && \
     docker push $DOCKER_REGISTRY_URL/keyssrv-dev:latest)

}

## the actual keyssrv docker image to run, this will push it to the dev ecr repo
function docker_push_stage {

ecr_login

(cd $DIR && \
     docker build -t keyssrv-stage . && \
     docker tag keyssrv-stage:latest $DOCKER_REGISTRY_URL/keyssrv-stage:latest && \
     docker push $DOCKER_REGISTRY_URL/keyssrv-stage:latest)

}

## the actual keyssrv docker image to run, this will push it to the dev ecr repo
function docker_push_prod {

ecr_login
BUILD_ID=`git rev-parse --short HEAD`

(cd $DIR && \
     docker build -t keyssrv . && \
     docker tag keyssrv-prod:latest $DOCKER_REGISTRY_URL/keyssrv:$BUILD_ID && \
     docker push $DOCKER_REGISTRY_URL/keyssrv:$BUILD_ID && \
     docker tag keyssrv:latest $DOCKER_REGISTRY_URL/keyssrv:stage && \
     docker push $DOCKER_REGISTRY_URL/keyssrv:stage)

}


function check_env_files {


 if [ ! -f "$DIR/.env-prod" ]; then

    touch $DIR/.env-prod

    cat > ~/.env-prod <<EOF
SMTP__PORT=465
SMTP__USER="test"
SMTP__PASSWORD="test"
SMTP__SSL=true
SMTP__HOST=localhost

STRIPE_PK=pk_test_iiHsg9PGcZEncavsOgUN6T0W
STRIPE_SK=sk_test_GDwYu5aIuls40b4vEHEN8NX0

EOF

   fi
}

function cli_docker_release {

 (cd $DIR
  repo=pkhubio/pkcli
  RELEASE=$(curl --silent "https://api.github.com/repos/$repo/releases/latest" | jq -r .tag_name)

  REV=$(git rev-parse --short HEAD)

  echo "User name ${DOCKER_USERNAME}"

  if [ -z "${DOCKER_PWD}" ]; then
   echo "DOCKER_PWD is not defined"
   exit -1
  fi

  echo ${DOCKER_PWD} | docker login -u="${DOCKER_USERNAME}" --password-stdin

  docker build -f DockerfileCli -t pkhub/pk-cli:latest -t pkhub/pk-cli:$RELEASE -t pkhub/pk-cli:$REV .


  docker push pkhub/pk-cli:latest
  docker push pkhub/pk-cli:$RELEASE
  docker push pkhub/pk-cli:$REV

 )

}

function cli_release {
## build the cli and release to github releases
set -e

 "$DIR/cli/pk/build.sh" release

 if [ -z "$GITHUB_TOKEN" ];
 then
    echo "GITHUB_TOKEN environment variable is not defined"
    exit -1
 fi


token=$GITHUB_TOKEN
repo=pkhubio/pkcli

RELEASE=$(curl --silent "https://api.github.com/repos/$repo/releases/latest" | jq -r .tag_name)


 ##TODO add the github release here
VERSION=$("$DIR/scripts/increment-version.sh" -m $RELEASE)

export VERSION

echo "MOVE FROM $RELEASE to $VERSION"


unset JSON

JSON=$(printf '{"tag_name": "%s", "target_commitish": "master", "name": "v%s", "body": "RELEASE %s", "draft": false, "prerelease": false}' "$VERSION" "$VERSION" "$VERSION" )

echo $JSON

upload_url=$(curl -s -H "Authorization: token $token"  \
     -d  "$JSON" \
     "https://api.github.com/repos/$repo/releases" | jq -r '.upload_url' )



echo $upload_url

upload_url="${upload_url%\{*}"


 for oos in $(ls "$DIR/cli/pk/releases"); do
   echo "$oos"
   for arch in $(ls "$DIR/cli/pk/releases/$oos"); do
    echo -e "\t$arch"
    FILE=$(ls "$DIR/cli/pk/releases/$oos/$arch/" | tail -n1)

    echo "FILE >> $FILE"

    echo "uploading asset to release to url : $upload_url"

unset LBL
LBL="$oos-${arch}_${VERSION}/$FILE"

echo  "LBL " $LBL
curl -s -H "Authorization: token $GITHUB_TOKEN"  \
        -H "Content-Type: application/octet-stream" \
        --data-binary  @"$DIR/cli/pk/releases/$oos/$arch/$FILE" \
        "$upload_url?name=$LBL&label=$LBL"
   done
 done


}

function aws_cli {

aws $@

}


function backupdb_local {


 if [ -z "$PGPASSWORD" ];
 then
  echo "PGPASSWORD must be defined"
  exit -1
 fi
 DATE=$(date +"%Y%m%d%H%M")
 psql  -p 5432 -h 127.0.0.1 -U postgres postgres | bzip2 -c &> db_"$DATE".sql && \
 ccrypt db_"$DATE".sql

}


function deploy_k8s_keyssrv {

  "$DIR/scripts/build.sh" deployk8s pkhub keyssrv

}

function noop {
    echo ""
}

check_env_files

check_compose

check_demo_keys_exist

CMD="$1"
shift

case "$CMD" in
    noop )
        noop
        ;;
    ecrlogin )
        ecr_login
        ;;
    start )
        start_components
        ;;
    stop )
        run_compose down -v
        ;;
    bash )
        run_dev_bash
        ;;
    test )
        run_dev_test
        ;;
    dockerpushstage )
        docker_push_stage
        ;;
    dockerpushprod )
        docker_push_prod
        ;;
    migrate )
        keyssrv_migrate
        ;;
    info )
        run_compose ps
        ;;
    build )
        package
        #package_cli
        ;;
    deploybase)
        deploy_base_images
        deploy_dev_docker
        ;;
    clidockerrelease)
        deploy_dev_docker
        ;;
    release)
       cli_release
       ;;
    clirelease)
       ## release the cli to github for download with install.sh
        cli_release
        ;;
    clidockerrelease)
       cli_docker_release
       ;;
    aws )
        aws_cli $@
        ;;
    renewcert )
     "$DIR/scripts/build.sh" renewcert
     ;;
    deployk8s )
      deploy_k8s_keyssrv
      ;;
    * )
        echo "Use commands: start|stop|info|logs|restartkeyssrv|build|bash|migrate"
        echo "Docker deploybase | deploydev => deploy dev base image | deploy Dockerfile-dev to ecr for code deploy and test"
#        echo "Clojurescript: cljs"
        exit -1
        ;;
esac
