#!/usr/bin/env bash

set -e

dir=$(cd -P -- "$(dirname -- "$0")" && pwd -P)


install_op () {

  kubectl apply -f https://raw.githubusercontent.com/spotahome/redis-operator/master/example/operator/all-redis-operator-resources.yaml

}


setup_failover () {

 (cd "$dir" \
  && kubectl apply -f "$dir/basic.yaml"
 )
}

install_op


setup_failover