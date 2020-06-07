(ns mansoon.db
  (:refer-clojure :exclude [get])
  (:require
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as sql]
   [next.jdbc.result-set :as result-set]))


(defn get-all-group-ids [db]
  (map :GALLERY/GALLERY_ID (jdbc/execute! db ["select distinct gallery_id from gallery order by gallery_id"])))


(defn create-schema [db]
  (jdbc/execute! db ["create table gallery (gallery_id nvarchar(255), uploader nvarchar(255), source nvarchar(255), facebook_url nvarchar(1024))"])
  (jdbc/execute! db ["create table gallery_images (id nvarchar(255), gallery_id nvarchar(255), url nvarchar(1024))"])
  (jdbc/execute! db ["create table gallery_tags (gallery_id nvarchar(255), tag nvarchar(255))"]))

(defn drop-schema [db]
  (jdbc/execute! db ["drop table if exists gallery_images"])
  (jdbc/execute! db ["drop table if exists gallery_tags"])
  (jdbc/execute! db ["drop table if exists gallery"]))

(defn exists? [db id]
  (jdbc/execute-one! db ["select 1 from gallery where gallery_id = ?" id]))

(defn get-gallery-by-tags [db tag]
  (map :GALLERY_TAGS/GALLERY_ID (jdbc/execute! db ["select distinct gallery_id from gallery_tags where tag = ? " tag])))

(defn get-tags [db]
  (map :GALLERY_TAGS/TAG (jdbc/execute! db ["select distinct tag from gallery_tags"])))


(defn put [db {:keys [gallery_id tags images] :as gallery}]
  (jdbc/with-transaction [tx db]
    (when (seq tags)
      (sql/insert-multi! tx :gallery_tags [:gallery_id :tag] (map (fn [tag] [gallery_id tag]) tags)))
    (when (seq images)
      (sql/insert-multi! tx :gallery_images [:gallery_id :id :url] (map (fn [img] [gallery_id (:id img) (:url img)]) images)))
    (sql/insert! tx :gallery (-> gallery
                                 (dissoc :tags :images)))))

(defn get [db id]
  (let [gallery (jdbc/execute-one! db ["select * from gallery where gallery_id = ? " id]
                                   {:builder-fn result-set/as-unqualified-lower-maps})
        images (jdbc/execute! db ["select url, id from gallery_images where gallery_id = ? " id]
                              {:builder-fn result-set/as-unqualified-lower-maps})
        tags (map :tag (jdbc/execute! db ["select tag from gallery_tags where gallery_id = ? " id]
                                      {:builder-fn result-set/as-unqualified-lower-maps}))]
    (when gallery
      (assoc gallery :images images :tags tags))))

(defn start
  [config]
  (prn ::start)
  (assoc config :db (jdbc/get-datasource {:dbtype "h2" :dbname "mansoon"})))

(defn stop
  [{:keys [db] :as config}]
  (prn ::stop)
  (dissoc config :db))
