(ns keyssrv.middleware
  (:require [keyssrv.env :refer [defaults]]
            [cheshire.generate :as cheshire]
            [cognitect.transit :as transit]
            [clojure.tools.logging :as log]
            [selmer.parser :as selmer]
            [keyssrv.config :as config]
            [keyssrv.layout :refer [error-page render*]]
            [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
            [ring.middleware.webjars :refer [wrap-webjars]]
            [ring.middleware.flash :refer [wrap-flash]]
            [muuntaja.core :as muuntaja]
            [muuntaja.format.json :refer [json-format]]
            [muuntaja.format.transit :as transit-format]
            [muuntaja.middleware :refer [wrap-format wrap-params]]
            [keyssrv.config :refer [env]]
            [keyssrv.sessions :as sessions]
            [ring-ttl-session.core :refer [ttl-memory-store]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [optimus.assets :as assets]
            [optimus.prime :as optimus]
            [optimus.optimizations :as optimizations]
            [optimus.strategies :as strategies]
            [keyssrv.i18n :as i18n]
            [selmer.parser :as parser]
            [taoensso.tempura :as tempura]
            [keyssrv.httpsecurity :as httpsec])

  (:import [org.joda.time ReadableInstant DateTime]
           (com.fasterxml.jackson.core JsonGenerator)))


;; This tag should be used as
;;;  {% i8n index/title %} and will translate to (tr [:index/title])
;;;  It relies on the wrap-i18n middleware
(parser/add-tag! :i18n
                 (fn [[k] context]
                   ((or (:tempura/tr context) (:tr context))
                     [(keyword k)])))

(defn wrap-i18n [handler]
  (tempura/wrap-ring-request handler {:tr-opts (i18n/i8n-config)}))

(defn wrap-internal-error [handler]
  (fn [req]
    (try
      (handler req)
      (catch Throwable t
        (log/error t (.getMessage t))
        (error-page {:status  500
                     :title   "Something very bad has happened!"
                     :message "We've dispatched a team of highly trained gnomes to take care of the problem."})))))


(defn redirect-back-to-referrer [request]
  (log/error (str "Error with anti forgery: redirecting to: " (get (:headers request) "referer")))
  (render* request "redirecthome.html" {:redirect-url (get (:headers request) "referer")}))

(defn anti-forgery-err-handler
  ([request] (redirect-back-to-referrer request))
  ([request _ _] (anti-forgery-err-handler request)))

(defn wrap-csrf [handler]
  (wrap-anti-forgery
    handler
    {:error-handler anti-forgery-err-handler}))

(def joda-time-writer
  (transit/write-handler
    (constantly "m")
    (fn [^ReadableInstant v] (.getMillis v))
    (fn [^ReadableInstant v] (String/valueOf (.getMillis v)))))

(cheshire/add-encoder
  DateTime
  (fn [^ReadableInstant c ^JsonGenerator jsonGenerator]
    (.writeString jsonGenerator (String/valueOf (.getMillis c)))))

(def restful-format-options
  (update
    muuntaja/default-options
    :formats
    merge
    {"application/json"
     json-format

     "application/transit+json"
     {:decoder [(partial transit-format/make-transit-decoder :json)]
      :encoder [#(transit-format/make-transit-encoder
                   :json
                   (merge
                     %
                     {:handlers {DateTime joda-time-writer}}))]}}))

(defn wrap-formats [handler]
  (let [wrapped (-> handler wrap-params (wrap-format restful-format-options))]
    (fn [request]
      ;; disable wrap-formats for websockets
      ;; since they're not compatible with this middleware
      ((if (:websocket? request) handler wrapped) request))))

;;Cache-Control: No-Store
;;The no-store directive means browsers aren’t allowed to cache a response
;;and must pull it from the server each time it’s requested.
;;This setting is usually used for sensitive data, such as personal banking details.

(defn cache-control-fn [resp]
  (assoc-in resp [:headers "Cache-Control"] "no-store"))

(defn wrap-cache-control [handler]
  (fn [request]
    (let [resp (handler request)]
      (cache-control-fn resp))))

(defn replace-vars [file]
  (selmer/render-file file config/env))

(defn get-assets []
  (concat
    (assets/load-bundle "public"
                        "styles.css"
                        [
                         ;"/css/screen.css"
                         "/css/front.css"
                         ;"/css/wizzard.css"
                         "/css/enjoyhint.css"
                         "/css/dataTables.bootstrap4.min.css"
                         ])

    (assets/load-bundles "public"
                         {"app.js" [
                                    "/js/wizzard.js"
                                    "/js/js-yaml.min.js"
                                    "/js/enjoyhint.min.js"
                                    "/js/ace.js"
                                    "/js/jquery.dataTables.min.js"
                                    "/js/dataTables.bootstrap4.min.js"
                                    "/js/app.js"
                                    ;"/js/front.js"

                                    ]})))

(defmulti middleware-setup (fn [handler] (:env env)))

(defmethod middleware-setup :dev [handler]
  (->
    handler

    httpsec/security-middleware

    ((:middleware defaults) )
    wrap-webjars
    wrap-i18n
    (wrap-defaults
      (-> site-defaults
          ;;j @TODO add back when env is prod
          ;(assoc-in [:security :hsts] {:max-age 31536000 :include-subdomains true})
          (assoc-in [:security :anti-forgery] false)
          (assoc-in [:session] {:flash        true
                                :cookie-name "keyssrv"
                                :store (sessions/session-store)
                                ;:cookie-attrs {:http-only true, :same-site :strict :secure true}
                                })))

    (optimus/wrap                                         ;; 14
      get-assets                                          ;; 15
      (if (:dev env)                                      ;; 16
        optimizations/none                                ;; 17
        optimizations/all)                                ;; 18
      (if (:dev env)                                      ;; 19
        strategies/serve-live-assets                      ;; 20
        strategies/serve-frozen-assets))                  ;; 21
    (ring.middleware.content-type/wrap-content-type)      ;; 22
    (ring.middleware.not-modified/wrap-not-modified)

    wrap-internal-error))

(defmethod middleware-setup :default [handler]
  (->
    handler

    httpsec/security-middleware

    ((:middleware defaults) )
    wrap-webjars
    wrap-i18n
    (wrap-defaults
      (-> site-defaults
          ;;j @TODO add back when env is prod
          (assoc-in [:security :hsts] {:max-age 31536000 :include-subdomains true})
          (assoc-in [:security :anti-forgery] false)
          (assoc-in [:session] {:flash        true
                                :cookie-name "keyssrv"
                                :store (sessions/session-store)
                                :cookie-attrs {:http-only true, :same-site :strict :secure true}
                                })))

    (optimus/wrap                                         ;; 14
      get-assets                                          ;; 15
      optimizations/all                                ;; 18
      strategies/serve-frozen-assets)                  ;; 21
    (ring.middleware.content-type/wrap-content-type)      ;; 22
    (ring.middleware.not-modified/wrap-not-modified)

    wrap-internal-error))


(defn wrap-base [handler]
  (middleware-setup handler))
