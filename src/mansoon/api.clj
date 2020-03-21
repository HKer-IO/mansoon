(ns mansoon.api
  (:require
    [clojure.set :as set]
    [clojure.string :as string]
    [jsonista.core :as json]
    [mansoon.db :as db]
    [org.httpkit.client :as http]
    [promesa.core :as p]
    [reaver :refer [parse extract-from text attr]])
  (:import
    (java.net
      URI)
    (javax.net.ssl
      SNIHostName
      SSLEngine
      SSLParameters)))


(def api "https://www.collaction.hk/lab/")

(def list-endpoint (str api "ajax_get_extradition_gallery_list"))

(def gallery-endpoint (str api "ajax_get_extradition_gallery"))

(def mapper (json/object-mapper {:decode-key-fn keyword}))


(defn sni-configure
  [^SSLEngine ssl-engine ^URI uri]
  (let [^SSLParameters ssl-params (.getSSLParameters ssl-engine)]
    (.setServerNames ssl-params [(SNIHostName. (.getHost uri))])
    (.setUseClientMode ssl-engine true)
    (.setSSLParameters ssl-engine ssl-params)))


(def client
  (http/make-client {:ssl-configurer sni-configure
                     :max-connections 5}))


(defn promise-callback
  [p]
  (fn [response]
    (if (:error response)
      (p/reject! p (:error response))
      (p/resolve! p response))))


(defn get-gallery-groups
  ([] (get-gallery-groups 0))
  ([offset]
   (let [p (p/deferred)
         _ (http/get list-endpoint
                     {:query-params {"offset" offset}
                      :client client}
                     (promise-callback p))]
     (-> p
         (p/then' (fn [resp]
                    (update resp :body json/read-value mapper)))
         (p/then' #(-> % :body :data))))))


(defn get-gallery-group-id
  [snippet]
  (extract-from (parse snippet) ".gallery-card"
                [:id :group-id]
                ".gallery-card-image-wrapper" (attr :data-id)
                ".gallery-card-image-wrapper" (attr :data-group-id)))


(defn get-all-gallery-groups
  [gallery-set]
  (loop [offset 0
         gset gallery-set]
    (let [groups @(get-gallery-groups offset)
          new-set (->> (:html groups)
                       (map get-gallery-group-id)
                       (map first)
                       (map :group-id)
                       (set))
          more? (:hasMore groups)
          new-items (set/difference new-set gset)]
      (if (and more? (seq new-items))
        (recur (+ offset 20)
               (set/union new-set gset))
        gset))))


(defn get-gallery-info
  [id]
  (let [p (p/deferred)
        _ (http/get gallery-endpoint
                    {:query-params {"id" id}
                     :client client}
                    (promise-callback p))]
    (-> p
        (p/then' (fn [resp]
                   (update resp :body json/read-value mapper)))
        (p/then' #(get-in % [:body :data :gallery])))))


(defn exclude-sys-keys
  [coll]
  (filter (fn [[k _]]
            (not (string/starts-with? k "idx-")))
          coll))


(defn index-vector
  [db tag-name]
  (reduce
    (fn [acc [k v]]
      (reduce (fn [acc2 t]
                (update acc2 t conj k))
              acc
              (tag-name v)))
    {}
    (exclude-sys-keys (db/all db))))


(defn index-unqi
  [db]
  (->> (reduce
         (fn [acc [k _]]
           (conj acc k))
         #{}
         (exclude-sys-keys (db/all db)))
       (vec)
       (sort #(compare (Integer/parseInt %2) (Integer/parseInt %1)))))


(defn search
  [db text limit-xf]
  (let [xf (comp (filter (fn idx-
                           [[k _]]
                           (not (string/starts-with? k "idx-"))))
                 (filter (fn ac-search
                           [[_ v]]
                           (some #(string/includes? % text) (:tags v))))
                 (map second)
                 limit-xf)]
    (into [] xf (db/all db))))


(defn main
  [db]
  (let [group-set (set (db/get db "idx-group-id"))
        new-group-set (get-all-gallery-groups group-set)]
    (do
      ; put all new records
      (doseq [batch (partition-all 100 new-group-set)]
        (-> (map (fn [id]
                   (if (nil? (db/get db id))
                     (p/then' (get-gallery-info id)
                              #(do
                                 (prn 'new id)
                                 (db/put db id %)))
                     (p/resolved nil)))
                 batch)
            (p/all)
            (deref)))
      ; update id idx
      (db/put db "idx-group-id" (index-unqi db))
      ; update tags idx
      (db/put db "idx-tags" (index-vector db :tags))
      (set/difference new-group-set group-set))))


(defn get-tags
  [db]
  (vec (keys (db/get db "idx-tags"))))


(defn get-gallery-by-tags
  [db tag]
  (get (db/get db "idx-tags") tag []))
