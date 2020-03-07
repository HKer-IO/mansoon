(ns mansoon.main
  (:require [mansoon.schedule :as schedule]
            [mansoon.web.handler :as handler]
            [mansoon.web.http :as http]
            [mansoon.db :as db]
            [mansoon.web.cache :as cache])
  (:gen-class))

(defn start []
  (prn ::start)
  (-> (db/start {})
      (schedule/start)
      (cache/start)
      (handler/start)
      (http/start)))

(defn stop [system]
  (-> system
      (http/stop)
      (handler/stop)
      (cache/stop)
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
