#!/usr/bin/env bash
## debug a k8s container
## kubectl run -i --tty busybox --image=busybox --restart=Never -- sh
set -e
dir=$(cd -P -- "$(dirname -- "$0")" && pwd -P)
export SOURCE_DIR="$dir"

## the short version of the git hash, used for docker tagging
BUILD_ID=`git rev-parse --short HEAD`
#

export DOCKER_REGISTRY_URL="349236026960.dkr.ecr.us-east-1.amazonaws.com"
export IMAGE="$DOCKER_REGISTRY_URL/keyssrv-prod"

deploy() {

#   NS="$1"
#   APP_NAME="$2"
#
#   if [ -z "$APP_NAME" ];
#   then
#    echo "Please provide NAME_SPACE APP_NAME params"
#    echo "e.g pkhub keyssrv"
#    exit -1
#   fi



   echo "Deploying $IMAGE"

   # build image


   (cd "$dir" && \
    ./kdeploy.sh updatek8sdeployimg $IMAGE pkhub keyssrv)
}

#setup_kube () {
#
##doctl kubernetes cluster kubeconfig save pk-sf-k8s
##aws eks --profile keyssrv --region eu-west-1 update-kubeconfig --name keyssrv
#
#}

kube_bash_once () {

kubectl run dev-bash --rm -i --tty --image ubuntu -- bash

}


kube_bash () {

kubectl -n pkhub exec -it ubuntu -- bash

}

pk_env () {

pk sh -s pkhub.io -n prod-env -i -- bash

}

create_secrets () {

 (cd "$dir" && \
  ./kdeploy.sh createsecrets)

}

applyk8s () {

 (cd "$dir" && \
  ./kdeploy.sh applyk8s)

}


#setup_kube

CMD="$1"
shift


case "$CMD" in
  k8s )
    kubectl $@
    ;;
  deployk8s )
    deploy $@
    ;;
  bashonce )
    kube_bash_once
    ;;
  bash )
    kube_bash
    ;;
  pkenv )
    pk_env
    ;;
  applyk8s )
    applyk8s
    ;;
  createsecrets )
    create_secrets
    ;;
  ceph )
    ceph
    ;;
  * )
  echo "./build.sh bash"
  ;;
esac
