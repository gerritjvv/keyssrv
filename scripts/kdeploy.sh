#!/usr/bin/env bash
########################################################################################################################
#  Deploy kubernetes files to AWS EKS using the AWS Authenticator
#
#  Folder structure:
#          This script should be at the top level directory,
#          it uses "find" to search for directories named "k8s"
#          Each "k8s" directory should have the format:
#               k8s/
#                   my-ns1/
#                       *.yaml <-- resources to apply to namespace my-ns1
#                       secrets.txt <-- references for secrets to be applied to this namespace
#                   my-ns2/
#                       *.yaml
#                       secrets.txt <-- references for secrets to be applied to this namespace
#                   *.yaml <-- default namespace or non namespace(d) resources
#                   namespaces.yml <-- create namespaces
#
#
#  Secrets
#     Secrets are expected to exist as environment variables and are referenced in the secrets.txt file
#     for each namespace.
#
#     The format of the file is:
#       <map>.<environment-variable>
#
#     e.g secrets.txt
#         myconfig.AWS_SECRET_ACCESS_KEY
#
#     can be used in a yaml file as:
#          env:
#        - name: AWS_SECRET_ACCESS_KEY
#          valueFrom:
#            secretKeyRef:
#              name: myconfig
#              key: AWS_SECRET_ACCESS_KEY
#
#  Docker tagging:
#           All images must be tagged by the git short version
#           use: BUILD_ID=`git rev-parse --short HEAD`
#
#  Namespace filtering:
#
#          Use the NAMESPACE environment variable to only apply yaml or secret files for a specified namespace.
#
#  Commands:
#           createsecrets       <-- find all secrets.txt files and create the secrets from the environment variables
#
#           applyk8s            <-- find apply k8s/*/*.yaml and k8s/*.yaml files and apply them
#           updatek8sdeployimg  <-- Change the deploy image to trigger a new deploy.
#                        args: Registry   <-- the ECR registry
#                              Namespace  <-- the k8s namespace for the deployment resource
#                              App        <-- the app name fo the deployment
#                        does: kubectl set image deployment.v1.apps/$app $module=${REGISTRY}:$BUILD_ID --namespace $ns
#
########################################################################################################################
set -e

dir=$(cd -P -- "$(dirname -- "$0")" && pwd -P)


## the short version of the git hash, used for docker tagging
BUILD_ID=`git rev-parse --short HEAD`


CMD="$1"


create_k8s_secret () {

NS="$1"
FILE="$2"

if [ -z "$FILE" ]; then
 echo "Must have a namespace and file as arguments"
 exit -1
fi

SECRETS_STR=""

while IFS='' read -r line || [[ -n "$line" ]]; do

   #skip comments
   [[ "$line" =~ ^#.*$ ]] && continue

    IFS=. read name key <<< "$line"
    key_val=${!key}

    [[ -z "$key" ]] && continue

    echo "GOT LINE $line"

    if [[ -z "name " || -z "key" ]]; then
        echo "Invalid line format $line"
        exit -1
    fi

    if [ -z "$key_val" ]; then
        echo "The environment variable $key must be defined"
        exit -1
    else
      SECRETS_STR="$SECRETS_STR --from-literal=$key=$key_val"
    fi


done < "$FILE"

if [ -z "$SECRETS_STR" ]; then
 echo "Nothing to apply"
else
 kubectl create secret generic $name $SECRETS_STR --namespace $NS --dry-run -o yaml | kubectl apply -f -
fi

}


create_k8s_secrets () {

 ##apply and reapply any namespaces
find "$dir" -iname "namespaces.yml" -exec kubectl apply -f {} \;

for d in $(find "$dir" -iname "k8s" -type d); do

  for namespace in $(find "$d/" -type d); do

     if [[  "${d}" != "${namespace%?}" ]] ; then

        for f in $(find "$d" -iname "secrets.txt"); do

           if [ -z "$NAMESPACE" ]; then
              echo "Apply script in namespace `basename $namespace`"

             create_k8s_secret `basename $namespace` $f
           else
             n=`basename $namespace`

             if [[ "$NAMESPACE" = "$n" ]]; then
              echo "Applying secrets for namespace $NAMESPACE"
              create_k8s_secret `basename $namespace` $f
             else
              echo "Skipping namespace $f"
             fi
           fi
         done

      else
        for f in $(find "$d" -iname "secrets.txt" -maxdepth 1); do
           echo "Apply script in namespace default"
           create_k8s_secret "default" $f
        done
      fi

   done
done
}

kubectl_apply_ext_ref () {

(cd "$dir" && \
 kubectl apply -f https://raw.githubusercontent.com/digitalocean/csi-digitalocean/master/deploy/kubernetes/releases/csi-digitalocean-v0.3.1.yaml
 )

}

kubectl_apply () {

FILE=$1
NS=$2

if [ -z "$NS" ]; then
 echo "Must have a FILE and Namepsace"
 exit -1
fi

## see https://linkerd.io/2/getting-started/
## we inject the linkerd mesh side car proxy into each deploy pod
## this will only affect deployment types and on other types is a noop
cat $FILE |  kubectl apply -n $NS -f -

}

apply_k8s () {

for d in $(find "$dir" -iname "k8s" -type d); do
           for namespace in $(find "$d/" -type d); do
             if [[  "${d}" != "${namespace%?}" ]] ; then

              for f in $(find "$namespace" -iname "*.yml"); do

                 if [ -z "$NAMESPACE" ]; then
                    echo "Applying yaml for namespace $NAMESPACE: $f"

                   kubectl_apply $f `basename $namespace`
                 else
                   if [[ "$NAMESPACE" = "`basename $namespace`" ]]; then
                    echo "Applying yaml $f"
                    kubectl_apply $f `basename $namespace`
                   else
                    echo "Skipping namespace $f"
                   fi
                 fi
              done

             else
              for f in $(find "$d" -iname "*.yml" -maxdepth 1); do
                 echo "Apply yaml $f"
                 kubectl apply -f $f
              done
             fi

         done
done

}

update_k8s_deploy_img () {

set -e

REGISTRY="$1"
ns="$2"
module="$3"

if [ -z "$module" ]; then

 echo "Require REGISTRY NAMESPACE APP"
 echo "APP is the last part of deployment.v1.apps/app"
 exit -1
fi


echo "Updating to image ${REGISTRY}:$BUILD_ID"
echo "NS $ns for module $module"

eval $(aws ecr get-login --region $AWS_REGION --no-include-email)
## pull to ensure that the id does exist
#if ! docker pull ${REGISTRY}:$BUILD_ID ; then
# echo "Error pulling docker image ${REGISTRY}:$BUILD_ID, please make sure it exists"
# exit -1
#fi

kubectl set image deployment.v1.apps/$module $module=${REGISTRY}:$BUILD_ID --namespace $ns
}

run_linkerd () {

 check_linkerd
 linkerd $@

}

case "$CMD" in
    apply8s_ext )
      kubectl_apply_ext_ref
      ;;
    createsecrets )
        create_k8s_secrets
          ;;
    applyk8s )
        apply_k8s
        ;;
    updatek8sdeployimg )
        shift
        update_k8s_deploy_img $@
        ;;
    * )

    echo "createsecrets|applyk8s|updatek8sdeployimg|linkerd"
    exit -1
       ;;


esac
