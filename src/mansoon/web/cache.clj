(ns mansoon.web.cache
  (:require
    [clojure.core.cache :as cache]))


(defn start
  [config]
  (prn ::start)
  (assoc config
         :web.cache/gallery
         (atom (cache/lru-cache-factory {:threshold 40000}))
         :web.cache/by-tag
         (atom (cache/soft-cache-factory {}))
         :web.cache/all-tags
         (atom (cache/soft-cache-factory {}))
         :web.cache/all-ids
         (atom (cache/soft-cache-factory {}))))


(defn stop
  [config]
  (prn ::stop)
  (dissoc config
          :web.cache/all-tags
          :web.cache/by-tag
          :web.cache/gallery
          :web.cache/all-ids))
