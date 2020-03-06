(ns keyssrv.layout
  (:require [selmer.parser :as parser]
            [selmer.filters :as filters]
            [markdown.core :refer [md-to-html-string]]
            [ring.util.http-response :refer [content-type ok header temporary-redirect]]
            [ring.util.anti-forgery :refer [anti-forgery-field]]
            [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [keyssrv.config :as conf]
            [optimus.link :as link]
            [jsonista.core :as json])
  (:import (keyssrv.util CryptoHelper)))

(def PARAM_SALT (delay (:param-salt conf/env)))


(defn plan-name-parser
  "Use to calculate a secure hash for parameters
  this can include the
  "
  [plan-type]
  (cond
    (= plan-type :free) "Free"
    (= plan-type :start) "Basic"
    (= plan-type :basic) "Developer"
    (= plan-type :pro) "Pro"
    :else "Free123"))


(defn param-hash
  "Use to calculate a secure hash for parameters
  this can include the
  "
  [& input]
  (let [salt "123"]
    (prn {:input (into [] input)})
    (CryptoHelper/sha256Hex salt input)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; utils functions

(defn add-to-buff ^StringBuilder [^StringBuilder buff lbl v]
  (when v
    (.append buff (str lbl ":"))
    (.append buff (str v))
    (.append buff \newline))

  buff)

(defn create-event-segment [event]
  (->
    (StringBuilder.)
    (add-to-buff "id" (:id event))
    (add-to-buff "event" (:event event))
    (add-to-buff "retry" (:retry event))
    (add-to-buff "data" (:data event))
    (.append \newline)
    .toString))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(parser/set-resource-path! (clojure.java.io/resource "templates"))
(parser/add-tag! :csrf-field (fn [_ _] (anti-forgery-field)))
(filters/add-filter! :markdown (fn [content] [:safe (md-to-html-string content)]))
(filters/add-filter! :param-hash param-hash)
(filters/add-filter! :plan-name-parser plan-name-parser)

(defn render-hash []
  (parser/render "{{\"a\"|param-hash:1:2:3:4:\"hi\"}}" {:id 1}))

(defn redirect-to [url]
  (temporary-redirect url))

(defn render
  "renders the HTML template located relative to resources/templates"
  [template & [params]]
  (content-type
    (ok
      (parser/render-file
        template
        (assoc params
          :page template
          :csrf-token *anti-forgery-token*)))
    "text/html; charset=utf-8"))


(defn add-prod [params env-type]
  (if-not  (= env-type :prod)
    params
    (assoc params :prod true)))

(defn render* [request template params]
  (let [uri (:uri request)
        args (assoc

               (add-prod params (keyword (or (:env conf/env) :prod)))

               :uri uri
               ;; i18n translations
               :tempura/tr (:tempura/tr request)
               ;; see keyssrv.middleware get-assets
               :css-urls (link/bundle-paths request ["styles.css"])
               :js-urls (link/bundle-paths request ["app.js"]))]

    (render template args)))


(defn error-page
  "error-details should be a map containing the following keys:
   :status - error status
   :title - error title (optional)
   :message - detailed error message (optional)

   returns a response map with the error page as the body
   and the status specified by the status key"
  [error-details]
  {:status  (:status error-details)
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body    (parser/render-file "error.html" error-details)})


(defn ajax-response [resp]
  (content-type
    (ok
      (json/write-value-as-string
        (merge
          resp
          {:csrf *anti-forgery-token*})))
    "application/json; charset=utf-8"))

(defn sse-event
  "event: {:id str/long :event str :data str}"
  [_ event & {:keys [end]}]
  {:status  (if end 204 200)
   :headers {"Content-Type"  "text/event-stream"
             "Cache-Control" "no-cache"}
   :body    (if (not-empty event)
              (create-event-segment event)
              "")})