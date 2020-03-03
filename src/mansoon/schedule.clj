(ns mansoon.schedule
  (:require [mansoon.api :as api]
            [chime :refer [chime-ch]]
            [clojure.core.async :as a :refer [<! go-loop]])
  (:import (java.time Instant Duration)))

(defn periodic-seq [^Instant start duration-or-period]
  (iterate #(.addTo duration-or-period %) start))

(defn start
  [{:keys [db] :as config}]
  (prn ::start)
  (let [chimes (chime-ch (periodic-seq (Instant/now) (Duration/ofMinutes 1)))
        ch (go-loop []
             (when-let [msg (<! chimes)]
               (prn "Start crawling at:" msg)
               (api/main db)
               (prn "End crawling at:" (Instant/now))
               (recur)))]
    (assoc config :schedule ch)))

(defn stop
  [{:keys [schedule] :as config}]
  (prn ::stop)
  (a/close! schedule)
  (dissoc config :schedule))
