(ns mansoon.web.cache
  (:require [clojure.core.cache :as cache]
            [msync.lucene :as lucene]
            [msync.lucene.analyzers :as analyzers]))

(defn create-gallery-index []
  (lucene/create-index! :type :disk
                        :path "lucene-index/"
                        :analyzer
                        (analyzers/standard-analyzer)))

(defn start [config]
  (prn ::start)
  (assoc config
    :web.cache/lucene
    (create-gallery-index)
    :web.cache/gallery
    (atom (cache/lru-cache-factory {:threshold 40000}))
    :web.cache/by-tag
    (atom (cache/soft-cache-factory {}))
    :web.cache/all-tags
    (atom (cache/soft-cache-factory {}))
    :web.cache/all-ids
    (atom (cache/soft-cache-factory {}))))

(defn stop [config]
  (prn ::stop)
  (dissoc config
          :web.cache/all-tags
          :web.cache/by-tag
          :web.cache/gallery
          :web.cache/all-ids
          :web.cache/lucene))
