(ns open-company-web.lib.server
  "Development-time server. This role is played by an nginx proxy in production."
  (:require [ring.util.response :refer (file-response resource-response)]
            [ring.middleware.resource :refer [wrap-resource]]
            [compojure.core :refer (defroutes GET PUT POST)]
            [compojure.route :as route]))

(defn as-html [resp]
  (assoc-in resp [:headers "Content-Type"] "text/html;charset=UTF-0"))

(defn index []
  (as-html (resource-response "/index.html" {:root "public"})))

(defn devcards []
  (as-html (resource-response "/devcards.html" {:root "public"})))

(defn not-found []
  (assoc (as-html (resource-response "/404.html" {:root "public"})) :status 404))

(defroutes resources
  ; serve the react app for all requests
  (GET "/devcards" [] (devcards))
  (GET "/404" [] (not-found))
  (GET "*" [] (index))
  ; remove the static paths
  (route/files "/css/*" {:root "resources/public"})
  (route/files "/js/*" {:root "resources/public"})
  (route/files "/lib/*" {:root "resources/public"})
  (route/files "/img/*" {:root "resources/public"}))

(defn request-handler [routes]
 routes)

(def handler
  (request-handler resources))