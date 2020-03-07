(ns mansoon.web.http
  (:require [ring.middleware.cors :as cors]
            [org.httpkit.server :as server]))

(defn start [{:http/keys [handler] :as config}]
  (prn ::start)
  (assoc config :http/server
                (let [app (-> handler
                              (cors/wrap-cors :access-control-allow-origin [#".*"]
                                              :access-control-allow-methods [:get]))]
                  (server/run-server app {:port 8080
                                          :thread 1}))))


(defn stop [{:http/keys [server] :as config}]
  (prn ::stop)
  (server)
  (dissoc config :http/server))
