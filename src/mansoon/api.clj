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

(defn promise-request [params]
  (let [p (p/deferred)
        _ (http/request params (promise-callback p))]
    p))


(defn get-gallery-groups-request
  ([offset] {:url list-endpoint
             :query-params {"offset" offset}
             :client client}))


(defn get-gallery-groups
  ([] (get-gallery-groups 0))
  ([offset]
   (-> (get-gallery-groups-request offset)
       (promise-request)
       (p/then' (fn [resp]
                  (update resp :body json/read-value mapper)))
       (p/then' #(-> % :body :data)))))


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
                       (mapcat get-gallery-group-id)
                       (map :group-id)
                       (set))
          more? (:hasMore groups)
          new-items (set/difference new-set gset)]
      (prn 'group-ids new-items)
      (if (and more? (seq new-items))
        (recur (+ offset 20)
               (set/union new-set gset))
        gset))))


(defn get-gallery-info-request [id]
  {:url gallery-endpoint
   :query-params {"id" id}
   :client client})


(defn get-gallery-info
  [id]
  (-> (get-gallery-info-request id)
      (promise-request)
      (p/then' (fn [resp]
                 (update resp :body json/read-value mapper)))
      (p/then' #(get-in % [:body :data :gallery]))))

(defn search
  [db text limit-xf])


(defn main
  [db]
  (let [group-set (set (db/get-all-group-ids db))
        new-group-set (get-all-gallery-groups group-set)]
    (doseq [batch (partition-all 10 new-group-set)]
      (-> (map (fn [id]
                 (prn id)
                 (if (nil? (db/exists? db id))
                   (p/then' (get-gallery-info id)
                            #(db/put db %))
                   (p/resolved nil)))
               batch)
          (p/all)
          (deref)))))


(defn get-tags
  [db]
  [])


(defn get-gallery-by-tags
  [db tag]
  [])
