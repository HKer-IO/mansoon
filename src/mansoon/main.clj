(ns mansoon.main
  (:require [mansoon.schedule :as schedule]
            [mansoon.web :as web]
            [mansoon.db :as db])
  (:gen-class))

(defn start []
  (-> (db/start {})
      (schedule/start)
      (web/start)))

(defn stop [system]
  (-> system
      (web/stop)
      (schedule/stop)
      (db/stop)))


(defn -main [& args]
  (start))
