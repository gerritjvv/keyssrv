## Overview

Redis is deployed using the redis operator from : https://github.com/spotahome/redis-operator


See: https://github.com/spotahome/redis-operator


## Build & Deploy

```bash
./build.sh
```


##  Connection to the created Redis Failovers
In order to connect to the redis-failover and use it, a Sentinel-ready library has to be used. This will connect through the Sentinel service to the Redis node working as a master. The connection parameters are the following:

```
url: rfs-pk-session
port: 26379
master-name: mymaster
```

```bash

#inside redis-cli
 SENTINEL get-master-addr-by-name mymaster
 SENTINEL slaves mymaster
```

To connect from local:

```bash
## rember to run pk sh -s pkhub.io -n ops -i -- bash
## then  doctl kubernetes cluster kubeconfig save pk-sf-k8s
kubectl port-forward rfs-pk-session 26379:26379
redis-cli -p 26379
 
```