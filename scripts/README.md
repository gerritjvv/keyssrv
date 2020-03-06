
# Requirements and setup

Latest setup:


EFS:

./k8s_setup/external-storage/aws/efs/deploy/rbac.yaml
./k8s_setup/external-storage/aws/efs/deploy/manifest.yaml


## Configuration variables

Before anything, ensure that the configuration variables required by
  * k8s/pkhub/ecr-creds-update.yaml
  * k8s/pkhub/keyssrv.yaml 
  * k8s/pkhub/letsencrypt.yaml
  
are all in the secrets configmap pkhub/keyssrv


The file ks8/pkbhu/secrets.txt contains all of the variables required.

```bash
./build.sh createsecrets
```


# ECR registry pull

the k8s/pkhub/ecr-creds-update.yml creates a cron job that runs every 6 hours to update the 
image pull registry secret with ecr updates token info for the default service account role
in the pkhub namespace.


# Certification

Important: Never specify the rules in anything else than the keyssrv.yml
To make the passthrough work first delete the ingress controllers and only use the keyssrv.yml ingress


https://github.com/gerritjvv/javaletsencrypt

Its configured to use the rook file system "myfs" and mounted to /etc/letsencrypt2/
 
 /etc/letsencrypt/live/pkhub.io/privkey.pem
 /etc/letsencrypt/live/pkhub.io/cert.pem
 /etc/letsencrypt/live/pkhub.io/fullchain.pem
 /etc/letsencrypt/live/pkhub.io/keystore.p12
 
 
The certbot uses the certbot digital ocean dns plugin to register and renew the certificate.
It relies on a ~/.digitalocean.ini file which is created automatically from the DIGITALOCEAN_ACCESS_TOKEN
environment variable.

This variable comes from the keyssrv.DIGITALOCEAN_ACCESS_TOKEN secret.

The renewal runs daily on a cron job at 10:00 to check and if any renew the cert. 
The keyssrv instance will check for updates to the p12 file and if any reload it into Jetty
automatically without restart.
 
# Kubernetes Cluster Create

The cluster is created with the conf.yaml using rke.
See the k8ssetup

The majority of cluster stuff will be added to the k8ssetup


The k8s is for application specific deployments.


# NFS

We have a single nfs droplet server.

With a floating ip of 138.197.225.9

See: https://vitux.com/install-nfs-server-and-client-on-ubuntu/

```bash

vim /etc/exports
exportfs -a
systemctl restart nfs-kernel-server

```