(ns mansoon.web.handler
  (:require [mansoon.db :as db]
            [mansoon.api :as api]
            [clojure.core.cache :as cache]
            [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(s/defschema Gallery
             {:tags                         [s/Str]
              :gallery_id                   s/Str
              :uploader                     (s/maybe s/Str)
              :images                       [{:id s/Str :url s/Str}]
              :source                       (s/maybe s/Str)
              :facebook_url                 (s/maybe s/Str)})

(s/defschema GallerySearchResult
  {:result [Gallery]
   :total s/Int})

(s/defschema TagSearchResult
  {:result [s/Str]
   :total s/Int})

(defn to-result-xf
  [page size {:keys [max-size]}]
  (let [page (max page 0)
        size (min size max-size)
        xf (comp (drop (* page size)) (take size))]
    xf))

(defn wrap-result [result count]
  {:result result
   :total count})

(defn to-result [all page size]
  (wrap-result
    (into [] (to-result-xf page size {:max-size 20}) all)
    (count all)))

(defn from-cache [cache update-fn]
  (fn [k]
    (swap! cache cache/through-cache k update-fn)
    (cache/lookup @cache k)))

(defn app [{:keys [db]
            :web.cache/keys [all-ids by-tag all-tags gallery]}]
  (let [get-by-id (from-cache gallery (partial db/get db))]
    (api
      {:coercion :schema
       :swagger
       {:ui "/"
        :spec "/swagger.json"
        :data {:info {:title "Collaction Extradition Gallery API"
                      :description "Collaction Extradition Gallery https://collaction.hk"
                      :contact {:name "Albert Lai"
                                :url "https://github.com/HKer-IO/mansoon"}
                      :license {:name "Eclipse Public License"
                                :url "http://www.eclipse.org/legal/epl-v10.html"}}
               :consumes ["application/json"]
               :produces ["application/json"]}}}

      (GET "/gallery/tag/:tag" []
        :return GallerySearchResult
        :path-params [tag :- s/Str]
        :query-params [{page :- s/Int 0}
                       {size :- s/Int 20}]
        :summary "List gallery under tag"
        (let [_ (swap! by-tag cache/through-cache tag (partial api/get-gallery-by-tags db))
              galleries (cache/lookup @by-tag tag)
              xf (comp (to-result-xf page size {:max-size 20})
                       (map get-by-id))]
          (ok
            (wrap-result (into [] xf galleries)
                         (count galleries)))))


      (GET "/gallery/latest" []
        :return GallerySearchResult
        :query-params [{page :- s/Int 0}
                       {size :- s/Int 20}]
        :summary "List latest galleries"
        (let [_ (swap! all-ids cache/through-cache "idx-group-id" (partial db/get db))
              ids (cache/lookup @all-ids "idx-group-id")
              xf (comp (to-result-xf page size {:max-size 20})
                       (map get-by-id))]
          (ok (wrap-result (into [] xf ids)
                           (count ids)))))

      (GET "/gallery/:id" []
        :return Gallery
        :path-params [id :- s/Str]
        :summary "Get gallery by id"
        (ok (get-by-id id)))

      (GET "/search" []
        :return [Gallery]
        :query-params [q :- s/Str
                       {page :- s/Int 0}
                       {size :- s/Int 5}]
        :summary "Search on gallery's tags"
        (ok
          (api/search db q (to-result-xf page size {:max-size 20}))))

      (GET "/tags" []
        :return TagSearchResult
        :query-params [{page :- s/Int 0}
                       {size :- s/Int 10}]
        :summary "Get all tags"
        (do
          (swap! all-tags cache/through-cache :all (fn [_] (api/get-tags db)))
          (ok (-> (cache/lookup @all-tags :all)
                  (to-result page size))))))))

(defn start [config]
  (assoc config :http/handler (app config)))

(defn stop [config]
  (dissoc config :http/handler))
