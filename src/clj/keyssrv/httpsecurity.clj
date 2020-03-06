(ns keyssrv.httpsecurity
  (:require [clojure.string :as string]))


(defn cc-policy-flatten ^String [k ls]
  (str (name k) " " (string/join \space ls) "; "))

(defn create-content-policy []
  (let [m {:default-src ["'none'"]

             :form-action ["'self'" "https://pkhub.io" "http://pkhub.io"]
             :object-src ["'none'"]
             :script-src ["'self'"
                          "*.stripe.com"
                          "www.google-analytics.com"
                          "stats.g.doubleclick.net"
                          "www.googletagmanager.com"
                          "*.vimeo.com" "*.vimeocdn.com"
                          ]
             :connect-src ["'self'" "*.vimeo.com" "www.google-analytics.com"
                           "vts.zohopublic.com"
                           "salesiq.zoho.com" "api.stripe.com" ]
             :img-src ["'self'"
                       "'self' data:"
                       "img.zohostatic.com"
                       "assets.capterra.com"
                       "*.vimeo.com" "*.vimeocdn.com"
                       "*.stripe.com" "www.google-analytics.com" "salesiq.zoho.com" "stats.g.doubleclick.net"]
             :style-src ["'self'" "'unsafe-inline'" "*.stripe.com"
                         "maxcdn.bootstrapcdn.com"
                         "css.zohostatic.com"
                          "fonts.googleapis.com"]
             :frame-src ["'self'" "*.vimeo.com" "*.vimeocdn.com"
                         "*.stripe.com"]
             :font-src ["'self'" "fonts.gstatic.com" "maxcdn.bootstrapcdn.com" "*.zohostatic.com"]}

        ^StringBuilder buff (reduce-kv (fn [^StringBuilder buff k v]
                                         (doto buff
                                           (.append (cc-policy-flatten k v)))) (StringBuilder.) m)]


    (str buff)))

(def CONTENT_SECURITY_POLICY (create-content-policy))

(defn add-headers [headers]
  (->
    headers
    (dissoc "server")
    (assoc "Content-Security-Policy" CONTENT_SECURITY_POLICY)
    (assoc "Expect-CT" "max-age=86400, enforce")
    (assoc "Feature-Policy" "'none'")
    ;(assoc "Access-Control-Allow-Origin" "https://pkhub.io" "https://*.stripe.com" "")
    ))

(defn security-middleware [handler]
  (fn [req]
    (update
      (handler req)
      :headers
      add-headers)))