# mykeys


## Front End

### Dashboard

The material dashboard compiles using http://koala-app.com/

## User visits

The user visits table record user registration and activity to check and sanitise active users from inactive users.

Events logged:
 On register: the first event is logged.
 On views.

Do not record creates

## Swagger

Run `keyssrv.test.pkcli/build`

This will redownload the swagger.json file and rebuild the golang

## Project

## Project plan


## Native App

See https://github.com/zserge/webview/blob/master/README.md



## TLS

https://webtide.com/conscrypting-native-ssl-for-jetty/


# Marketing

## Sponsor ships


## Build

### Environment variables

`.env`

All development and test keys are in this environment file.
It is safe to commit and push it.

`.env-prod`  

Some production keys are required even in dev/test (e.g smpt2go).  


`prod-env`  

All production keys are in this file.


### Registry

All docker images are deployed to AWS ECR

### Requirements

All builds have base images that contain all utility programs and libraries.  


To deploy the base images to ECR run:

```
./build.sh deploybase 
```


## API

### Stripe And Payments

The stripe.js is treated as a selmer template.

All environment variables and config variables (config/env) are passed directly to the template render in the middleware.

We use this to swap out the correct `{{STRIPE}}` id value.

### Page optimizations

See https://github.com/magnars/optimus

### Password sharing


User A shares the group-master-key with User B by doing:

   decrypting gmk = password_groups_users.user_group_master_key_enc 
   encrypting gmk_enc = gmk with mykeysrv_public_key, sent during "share request operation",
   saving the record to redis =  invite_id, invite_jwe(from: UserA, to: UserB, gmk_enc: gmk_enc, ttl=day) encrypted with mykeysrv_public_key
   
      
   The invite_id is sent to the user in an email he/she needs to click on:
      onces clicked on he/she is redirected to a password_invite_enc screen
          Asked to enter his/her password:
             The server will re-authenticate the user.
             
             The server will read the record from redis, 
               decrypt the JWE, 
               decrypt the gmk_enc, encrypt it with the user's password and save it to password_groups_users.user_group_master_key_enc
               
             delete the entry from redis
   
### Login And Session

keyssrv.users.auth/wrap-authentication checks that the user is logged in
otherwise a redirect to the login page is done.

keyssrv.users.auth/login-user-session adds the user to the session identity,
also checks that the user fits the required schema in schemas.


keyssrv.routes.users/user-record takes the db users record + plan as :plan 
and only returns the items thats meant for the session,
this allows us to fine tune how a session looks

All sessions must conform to the keyssrv.schemas.core/USER-SESSION-IDENTITY schema

The session store is configured in keyssrv.middleware/wrap-base

### CSS and JS Bundles

see keyssrv.middleware get-assets and keyssrv.layout/render*

```clojure

(defn get-assets []
  (concat
    (assets/load-bundle "public"
                        "styles.css"
                        ["/css/screen.css"
                         "/css/wizzard.css"])

    (assets/load-bundles "public"
                         {"app.js" ["/js/app.js"
                                    "/js/stripe.js"
                                    "/js/wizzard.js"]})))

;; This makes the bundles
;; - app.js and styles.css

```


### Trouble shooting

#### Login goes back to login after reaching home

Check that the session store is saving data correctly


## Support Tickets

See https://desk.zoho.com/support/vaulthub/ShowHomePage.do#Cases/q/duedate

#Docs and Styles

https://content-guide.18f.gov/plain-language/

# DB Migrations

For development on (start) the migrations are called.

See http://www.luminusweb.net/docs/migrations.html

To create a new migration run 
```clojure
;;run (start) first
(create-migration "add-users-table")
;;creates file resources/migrations/201506120401-add-users-table.up.sql

```


# Unique Id generation

32 char output:

```hexdump -n 16 -e '4/4 "%08X" 1 "\n"' /dev/random```



# FrontEnd Dev

## Hints

view-source:http://xbsoftware.github.io/enjoyhint/example1/script.js
view-source:http://xbsoftware.github.io/enjoyhint/example1.html
https://github.com/xbsoftware/enjoyhint/

## Styles

view-source:https://content-guide.18f.gov/plain-language/


# References

 * https://hpbn.co/server-sent-events-sse/
 
 
#Icons 

See: https://www.flaticon.com/search?word=secure

## Credit Authors

<div>Icons made by <a href="https://www.flaticon.com/authors/monkik" title="Safe box">Safe box</a> from <a href="https://www.flaticon.com/"     title="Flaticon">www.flaticon.com</a> is licensed by <a href="http://creativecommons.org/licenses/by/3.0/"     title="Creative Commons BY 3.0" target="_blank">CC 3.0 BY</a></div>
<div>Icons made by <a href="https://www.flaticon.com/authors/smashicons" title="Smashicons">Smashicons</a> from <a href="https://www.flaticon.com/" title="Flaticon">www.flaticon.com</a> is licensed by <a href="http://creativecommons.org/licenses/by/3.0/" title="Creative Commons BY 3.0" target="_blank">CC 3.0 BY</a></div


# Distribution

## Brew

http://kylebebak.github.io/post/distribute-program-via-homebrew>
