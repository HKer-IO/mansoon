(ns mansoon.schedule
  (:require
    [chime :refer [chime-ch]]
    [clojure.core.async :as a :refer [<! go-loop]]
    [clojure.core.cache :as cache]
    [mansoon.api :as api]
    [mansoon.db :as db]
    [msync.lucene :as lucene])
  (:import
    (java.time
      Duration
      Instant)))


(defn periodic-seq
  [^Instant start duration-or-period]
  (iterate #(.addTo duration-or-period %) start))


(def gallery->doc-xf
  (comp (map second)
        (filter map?)
        (map #(dissoc % :images :facebook_url))))


(defn start
  [{:keys [db]
    :web.cache/keys [all-ids by-tag all-tags lucene]
    :as config}]
  (prn ::start)
  (let [chimes (chime-ch (periodic-seq (Instant/now) (Duration/ofMinutes 1))
                         {:ch (a/chan (a/sliding-buffer 1))})
        ch (go-loop []
             (when-let [msg (<! chimes)]
               (prn "Start crawling at:" msg)
               (try
                 (when (> (count (api/main db)) 0)
                   ; evict cache
                   (swap! all-ids cache/seed {})
                   (swap! by-tag cache/seed {})
                   (swap! all-tags cache/seed {}))
                 (lucene/index! lucene
                                (into [] gallery->doc-xf (db/all db))
                                {:stored-fields [:gallery_id]
                                 :re-create? true})
                 (catch Exception ex (prn ex)))
               (prn "End crawling at:" (Instant/now))
               (recur)))]
    (assoc config :schedule [chimes ch])))


(defn stop
  [{:keys [schedule] :as config}]
  (prn ::stop)
  (doseq [ch schedule]
    (a/close! ch))
  (dissoc config :schedule))
