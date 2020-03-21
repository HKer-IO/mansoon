(ns dev
  (:require
    [clojure.tools.namespace.repl :refer [refresh]]
    [mansoon.main]))


(clojure.tools.namespace.repl/set-refresh-dirs "src")

(defonce system (atom {}))


(defn start
  []
  (reset! system (mansoon.main/start))
  :started)


(defn stop
  []
  (swap! system mansoon.main/stop)
  :stopped)


(defn reset
  []
  (when @system (stop))
  (start)
  :reset)
