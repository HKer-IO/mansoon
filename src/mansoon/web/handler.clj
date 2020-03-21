(ns mansoon.web.handler
  (:require
    [clojure.core.cache :as cache]
    [mansoon.api :as api]
    [mansoon.db :as db]
    [msync.lucene :as lucene]
    [msync.lucene.document :as ld]
    [muuntaja.core :as m]
    [reitit.coercion.schema]
    [reitit.ring :as ring]
    [reitit.ring.coercion :as coercion]
    [reitit.ring.middleware.dev]
    [reitit.ring.middleware.exception :as exception]
    [reitit.ring.middleware.muuntaja :as muuntaja]
    [reitit.ring.middleware.parameters :as parameters]
    [reitit.swagger :as swagger]
    [reitit.swagger-ui :as swagger-ui]
    [ring.middleware.cors :as cors]
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


(defn wrap-result
  [result count]
  {:result result
   :total count})


(defn to-result
  [all page size]
  (wrap-result
    (into [] (to-result-xf page size {:max-size 20}) all)
    (count all)))


(defn from-cache
  [cache update-fn]
  (fn [k]
    (swap! cache cache/through-cache k update-fn)
    (cache/lookup @cache k)))


(defn routes
  [{:keys [db]
    :web.cache/keys [all-ids by-tag all-tags gallery lucene]}]
  (let [get-by-id (from-cache gallery (partial db/get db))]
    [["/gallery"
      ["/tag/:tag" {:get {:summary "List gallery under tag"
                          :coercion reitit.coercion.schema/coercion
                          :parameters {:path {:tag s/Str}
                                       :query {(s/optional-key :page) s/Int
                                               (s/optional-key :size) s/Int}}
                          :responses {200 {:body GallerySearchResult}}
                          :handler
                          (fn [{{{:keys [tag]} :path
                                 {:keys [page size] :or {page 0 size 20}} :query} :parameters}]
                            (let [_ (swap! by-tag cache/through-cache tag (partial api/get-gallery-by-tags db))
                                  galleries (cache/lookup @by-tag tag)
                                  xf (comp (to-result-xf page size {:max-size 20})
                                           (map get-by-id))]
                              (ok
                                (wrap-result (into [] xf galleries)
                                             (count galleries)))))}}]
      ["/latest" {:get {:summary "List latest galleries"
                        :coercion reitit.coercion.schema/coercion
                        :parameters {:query {(s/optional-key :page) s/Int
                                             (s/optional-key :size) s/Int}}
                        :responses {200 {:body GallerySearchResult}}
                        :handler
                        (fn [{{{:keys [page size] :or {page 0 size 20}} :query} :parameters}]
                          (let [_ (swap! all-ids cache/through-cache "idx-group-id" (partial db/get db))
                                ids (cache/lookup @all-ids "idx-group-id")
                                xf (comp (to-result-xf page size {:max-size 20})
                                         (map get-by-id))]
                            (ok (wrap-result (into [] xf ids)
                                             (count ids)))))}}]
      ["/:id" {:get {:summary    "Get gallery by id"
                     :coercion   reitit.coercion.schema/coercion
                     :parameters {:path {:id s/Str}}
                     :responses  {200 {:body Gallery}}
                     :handler    (fn [{{{:keys [id]} :path} :parameters :as req}]
                                   (ok (get-by-id id)))}}]]
     ["/search" {:get {:summary    "Search on gallery's tags"
                       :coercion   reitit.coercion.schema/coercion
                       :parameters {:query {:q                     s/Str
                                            (s/optional-key :page) s/Int
                                            (s/optional-key :size) s/Int}}
                       :responses  {200 {:body [Gallery]}}
                       :handler    (fn [{{{:keys [q page size] :or {page 0 size 20}} :query} :parameters}]
                                     (ok
                                       (map :hit
                                            (lucene/search @lucene
                                                           {:tags q}
                                                           {:result-per-page size
                                                            :page            page
                                                            :hit->doc        #(-> %
                                                                                  (ld/document->map :multi-fields [:gallery_id])
                                                                                  :gallery_id
                                                                                  first
                                                                                  get-by-id)}))))}}]
     ["/tags" {:get {:summary "Get all tags"
                     :coercion   reitit.coercion.schema/coercion
                     :parameters {:query {(s/optional-key :page) s/Int
                                          (s/optional-key :size) s/Int}}
                     :responses  {200 {:body TagSearchResult}}
                     :handler (fn [{{{:keys [page size] :or {page 0 size 20}} :query} :parameters}]
                                (do
                                  (swap! all-tags cache/through-cache :all (fn [_] (api/get-tags db)))
                                  (ok (-> (cache/lookup @all-tags :all)
                                          (to-result page size)))))}}]
     ["" {:no-doc true}
      ["/swagger.json" {:get (swagger/create-swagger-handler)
                        :swagger {:info {:title "Collaction Extradition Gallery API"
                                         :description "Collaction Extradition Gallery https://collaction.hk"
                                         :contact {:name "Albert Lai"
                                                   :url "https://github.com/HKer-IO/mansoon"}
                                         :license {:name "Eclipse Public License"
                                                   :url "http://www.eclipse.org/legal/epl-v10.html"}}
                                  :consumes ["application/json"]
                                  :produces ["application/json"]}}]
      ["/*" {:get (swagger-ui/create-swagger-ui-handler)}]]]))


(defn app
  [options]
  (ring/ring-handler
    (ring/router
      (routes options)
      {:conflicts nil
       ;:reitit.middleware/transform reitit.ring.middleware.dev/print-request-diffs
       :data      {:muuntaja   m/instance
                   :middleware [#(cors/wrap-cors % :access-control-allow-origin [#".*"]
                                                 :access-control-allow-methods [:get :put :post :delete])
                                swagger/swagger-feature
                                parameters/parameters-middleware
                                muuntaja/format-response-middleware
                                exception/exception-middleware
                                coercion/coerce-response-middleware
                                coercion/coerce-request-middleware]}})))


(defn start
  [config]
  (assoc config :http/handler (app config)))


(defn stop
  [config]
  (dissoc config :http/handler))
