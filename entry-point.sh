#!/usr/bin/env bash

DIR=$(cd -P -- "$(dirname -- "$0")" && pwd -P)

set -e

## Allows us to either specify the keys or download them from
### the AWS key service

#if [ -z "$JVM_OPTS" ]; then
#JVM_OPTS="-Xmx1024m"
#fi


## need to add -Dorg.conscrypt.native.workdir=/keyssrv  see
### https://github.com/google/conscrypt/issues/449

##remember to set the jetty.host to 0.0.0.0 for docker instances

(cd "$DIR" && \
 JAR=`ls $DIR/keyssrv*.jar | head -n1` && \
 LIBS=`ls $DIR/lib/*.jar | tr '\n' ':'`  && \
 RESOURCES="$DIR/resources/" && \
 CP="$JAR:$LIBS:$RESOURCES" && \
 java  -XX:+UnlockDiagnosticVMOptions  -XX:+UseAESIntrinsics \
 -Djdk.tls.ephemeralDHKeySize=2048 \
 -Djetty.host=0.0.0.0 \
 -Dlogback.configurationFile="$DIR/logback.xml" \
 -cp "$CP" keyssrv.core $@
 )
