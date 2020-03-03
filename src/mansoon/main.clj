(ns mansoon.main
  (:require [mansoon.schedule :as schedule]
            [mansoon.web.handler :as handler]
            [mansoon.web.http :as http]
            [mansoon.db :as db])
  (:gen-class))

(defn start []
  (prn ::start)
  (-> (db/start {})
      (schedule/start)
      (handler/start)
      (http/start)))

(defn stop [system]
  (-> system
      (http/stop)
      (handler/stop)
      (schedule/stop)
      (db/stop))
  (prn ::stop))

(defn await-system
  [system]
  (.addShutdownHook (Runtime/getRuntime)
                    (new Thread ^Runnable #(do (stop system)
                                               (shutdown-agents))))
  (.join (Thread/currentThread)))

(defn -main [& args]
  (-> (start)
      (await-system)))
