

We have a droplet with ubuntu and nfs installed on it.

See: https://vitux.com/install-nfs-server-and-client-on-ubuntu/

```bash

vim /etc/exports
exportfs -a
systemctl restart nfs-kernel-server

```