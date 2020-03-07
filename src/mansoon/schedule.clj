(ns mansoon.schedule
  (:require [mansoon.api :as api]
            [chime :refer [chime-ch]]
            [clojure.core.async :as a :refer [<! go-loop]]
            [clojure.core.cache :as cache])
  (:import (java.time Instant Duration)))

(defn periodic-seq [^Instant start duration-or-period]
  (iterate #(.addTo duration-or-period %) start))

(defn start
  [{:keys [db]
    :web.cache/keys [all-ids by-tag all-tags]
    :as config}]
  (prn ::start)
  (let [chimes (chime-ch (periodic-seq (Instant/now) (Duration/ofMinutes 1)))
        ch (go-loop []
             (when-let [msg (<! chimes)]
               (prn "Start crawling at:" msg)
               (when (> (count (api/main db)) 0)
                 ; evict cache
                 (swap! all-ids cache/seed {})
                 (swap! by-tag cache/seed {})
                 (swap! all-tags cache/seed {}))
               (prn "End crawling at:" (Instant/now))
               (recur)))]
    (assoc config :schedule ch)))

(defn stop
  [{:keys [schedule] :as config}]
  (prn ::stop)
  (a/close! schedule)
  (dissoc config :schedule))
