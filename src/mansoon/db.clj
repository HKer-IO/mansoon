(ns mansoon.db
  (:refer-clojure :exclude [get])
  (:require [taoensso.nippy :as nippy])
  (:import (com.oath.halodb HaloDB HaloDBOptions Record)))

(defn- set-max-file-size [^HaloDBOptions options opt-map]
  (when (:max-file-size opt-map)
    (.setMaxFileSize options (:max-file-size opt-map)))
  options)

(defn ^HaloDBOptions map-to-halo-options
  [opt-map]
  (doto (HaloDBOptions.)
    (set-max-file-size opt-map)))

(defn open [^String dir options]
  (HaloDB/open dir (map-to-halo-options options)))

(defn put [^HaloDB db key value]
  (.put db (nippy/freeze key) (nippy/freeze value)))

(defn get [^HaloDB db key]
  (some-> (.get db (nippy/freeze key))
          (nippy/thaw)))

(defn delete [^HaloDB db key]
  (.delete db (nippy/freeze key)))

(defn all [^HaloDB db]
  (map (fn [^Record kv]
         [(some-> (.getKey kv) (nippy/thaw))
          (some-> (.getValue kv) (nippy/thaw))])
       (iterator-seq (.newIterator db))))

(defn start [config]
  (assoc config :db (open "tmp" {})))

(defn stop [{:keys [db] :as config}]
  (.close db)
  (dissoc config :db))
