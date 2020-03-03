(ns mansoon.web.handler
  (:require [mansoon.db :as db]
            [mansoon.api :as api]
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

(defn to-result [all page size]
    {:result (into [] (to-result-xf page size {:max-size 20}) all)
     :total (count all)})

(def mem-get-tags (memoize api/get-tags))

(defn app [{:keys [db]}]
  (api
    {:coercion :schema
     :swagger
     {:ui "/"
      :spec "/swagger.json"
      :data {:info {:title "Collaction Extradition Gallery API"
                    :description "Collaction Extradition Gallery https://collaction.hk"
                    :contact {:name "Albert Lai"}
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
      (ok
        (-> (map #(db/get db %)
                 (api/get-gallery-by-tags db tag))
            (to-result page size))))

    (GET "/gallery/:id" []
      :return Gallery
      :path-params [id :- s/Str]
      :summary "Get gallery by id"
      (ok (db/get db id)))

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
      (ok (-> (mem-get-tags db)
              (to-result page size))))))

(defn start [config]
  (assoc config :http/handler (app config)))

(defn stop [config]
  (dissoc config :http/handler))
