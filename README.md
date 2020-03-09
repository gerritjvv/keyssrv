# keyssrv

This is a secrets manager like [AWS Secrets Manager](https://aws.amazon.com/secrets-manager/) but different.

I'm hosting it at https://pkhub.io

## Encryption and keys

When you register as a user a encryption key is created, which is encrypted with your password.   
Each safe you create has its own encryption key i.e 1 encryption key per safe. The safe's encryption key
is then encryption with your own encryption key.

So to read the content of a safe you need:  

```
user-password = read
user-encryption-key = decrypt( encrypted-user-key, user-password)
safe-encryption-key = decrypt( safe.encrypted-key, user-encryption-key)

plain-text = decrypt( encrypted-data, safe-encryption-key)
```


## Sharing safes

When you share a safe you share the encryption key of the safe with a user. The encryption key is encrypted
with the user you share it with's encryption key.


For example:

````
UserA share SafeA with UserB

userA-password = read 
userA-encryption-key = decrypt( encrypted-userA-key, userA-password)
safeA-encryption-key = decrypt( userA-safeA.encrypted-key, userA-encryption-key)

# this is stored in redis on a timeout
safeA-encrypted-key-share = encrypt(safeA-encryption-key, system-key)

# when user B accepts
userB-password = read 
userB-encryption-key = decrypt( encrypted-userB-key, userB-password)
safeA-encryption-key = decrypt(safeA-encrypted-key-share, system-key)

userB-safeA.encrypted-key = encrypt( safeA-encryption-key, userB-encryption-key)
````

# Hosted service

I host this on AWS and behind a [CloudFlare Proxy](https://support.cloudflare.com/hc/en-us/articles/205177068-How-does-Cloudflare-work-). CloudFlare's proxy provides extra security and automatically protects against certain online attacks. This was also the only service I found I could trust and that supports DNSSec. 

# License

This code is made available under the APACHE-2 License.  
https://www.apache.org/licenses/LICENSE-2.0



