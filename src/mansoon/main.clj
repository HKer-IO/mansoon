(ns mansoon.main
  (:gen-class)
  (:require
    [mansoon.db :as db]
    [mansoon.schedule :as schedule]
    [mansoon.web.cache :as cache]
    [mansoon.web.handler :as handler]
    [mansoon.web.http :as http]))


(defn start
  []
  (prn ::start)
  (-> (db/start {})
      (cache/start)
     #_(schedule/start)
      (handler/start)
      (http/start)))


(defn stop
  [system]
  (-> system
      (http/stop)
      (handler/stop)
      #_(schedule/stop)
      (cache/stop)
      (db/stop))
  (prn ::stop))


(defn await-system
  [system]
  (.addShutdownHook (Runtime/getRuntime)
                    (new Thread ^Runnable #(do (stop system)
                                               (shutdown-agents))))
  (.join (Thread/currentThread)))


(defn -main
  [& args]
  (-> (start)
      (await-system)))
