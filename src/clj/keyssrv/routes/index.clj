(ns keyssrv.routes.index
  (:require [keyssrv.users.auth :as auth]

            [keyssrv.layout :as layout]
            [keyssrv.securesend.index :as ss-index]
            [clojure.tools.logging :as log]
            [keyssrv.middleware :as middleware])
  (:import (keyssrv.util Utils)
           (org.apache.commons.lang3 StringUtils)))

(defn sanitize [page]
  (let [page' (StringUtils/trimToNull (str page))
        page'' (when page' (Utils/sanitizePath (str page')))]

    (when page''
      (str page'' ".html"))))

(defn view* [template {:keys [flash] :as request}]
  (let [user (auth/user-logged-in? request)]

    (layout/render* request
                    template
                    (merge
                      {:user      user
                       :user-name (:user-name user)}
                      (select-keys flash [:name :message :errors])))))

(defn view [request]
  (view* "front/index.html" request))

(defn view-pricing [request]
  (view* "front/pricing.html" request))

(defn view-about [request]
  (view* "front/about-us.html" request))


(defn view-contact [request]
  (view* "front/contact-us.html" request))

(defn usecases
  "View any page in the usecases directory"
  [request page]
  (view* (str "front/usecases/" (sanitize page)) request))

(defn -securesend-read [request]
  (let [{:keys [msg error need-code params]} (ss-index/securesend-read request)]
    (layout/render*
      request
      "securesend/index.html"
      (merge
        {:show-msg msg :errors (when error [error]) :need-code need-code}
        params
        (select-keys (:flash request) [:name :message :errors])))))


(defn securesend
  "View any page in the usecases directory"
  [request]
  (cond
    (and (:k (:params request)))
    (-securesend-read request)
    :else
    (view* "securesend/index.html" request)))

(defn securesend-post [request]

  (prn {:params (:params request)})

  (let [link (ss-index/securesend request)]
    (layout/render* request
                    "securesend/index.html"
                    (merge
                      {:show-link link}
                      (select-keys (:flash request) [:name :message :errors])))))
