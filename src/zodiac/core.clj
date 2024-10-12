(ns zodiac.core
  (:require [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [dev.onionpancakes.chassis.core :as chassis]
            [integrant.core :as ig]
            [malli.core :as m]
            [malli.error :as me]
            [malli.util :as mu]
            [muuntaja.core :as muuntaja]
            [reitit.coercion.malli]
            [reitit.core :as r]
            [reitit.dev.pretty :as pretty]
            [reitit.ring]
            [reitit.ring.coercion :as coercion]
            [reitit.ring.middleware.dev :as dev]
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.multipart :as multipart]
            [reitit.ring.middleware.muuntaja :as muuntaja.middle]
            [reitit.ring.middleware.parameters :as parameters]
            [ring.adapter.jetty9 :as jetty]
            [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
            [ring.middleware.cookies :refer [wrap-cookies]]
            [ring.middleware.flash :refer [wrap-flash]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.session.cookie :refer [cookie-store]]))

;; These variables are bound to the current request.
(def ^:dynamic *request* nil)
(def ^:dynamic *router* nil)
(def ^:dynamic *session* nil)

(defn html-response
  "Return an HTML ring response. Attempt to render the body as HTML unless its
  already a string."
  ([doc]
   (html-response 200 doc))
  ([status doc]
   {:status status
    :headers {"content-type" "text/html"}
    :body (cond
            (string? doc) doc
            :else (str chassis/doctype-html5
                       (chassis/html doc)))}))

(defn json-response
  "Return a JSON ring response. Attempt to render the body as JSON unless its
  already a string."
  ([data]
   (json-response 200 data))
  ([status data]
   {:status status
    :headers {"content-type" "application/json"}
    :body (cond
            (string? data) data
            :else (json/write-str data))}))

(defn url-for
  "Return a url string give a route name and option arg and query params. This
  functions uses the router for the current request bound to *request*. If you
  need to use a different router then temporary bind *router* to your  the route name  this url-for "
  ([name-or-path]
   (url-for name-or-path nil nil))
  ([name-or-path args]
   (url-for name-or-path args nil))
  ([name-or-path args query-params]
   (if (string? name-or-path)
     name-or-path
     (-> *router*
         (r/match-by-name name-or-path args)
         (r/match->path query-params)))))

(defn wrap-context [handler context]
  (fn [request]
    (-> request
        (assoc ::context context)
        (handler))))

(defn render-html-middleware [handler]
  (fn [request]
    (let [response (handler request)]
      (if (vector? response)
        (html-response response)
        response))))

(defn bind-globals-middlware [handler]
  (fn [{:keys [session ::r/router] :as request}]
    (binding [*request* request
              *router* router
              *session* session]
      (handler request))))

(defmethod ig/init-key ::cookie-store [_ {:keys [secret]}]
  (cookie-store {:key (cond
                        (bytes? secret) secret
                        (string? secret) (.getBytes secret))}))


;; type hierarchy
(derive ::error ::exception)
(derive ::failure ::exception)
(derive ::horror ::exception)

(defn- exception-handler [message exception _request]
  (log/error (str message "\n" exception))
  (log/error exception)
  {:status 500
   :body "Unknown error"})

(def exception-middleware
  (exception/create-exception-middleware
   (merge exception/default-handlers
          {;; ex-data with :type ::error
           ::error (partial exception-handler "error")

           ;; ex-data with ::exception or ::failure
           ::exception (partial exception-handler "exception")

           ;; SQLException and all it's child classes
           java.sql.SQLException (partial exception-handler "sql-exception")

           ;; override the default handler
           ::exception/default (partial exception-handler "default")

           ;; print stack-traces for all exceptions
           ::exception/wrap (fn [handler e request]
                              (println "ERROR" (pr-str (:uri request)))
                              (handler e request))})))

(defmethod ig/init-key ::middleware [_ {:keys [context session-store cookie-attrs]}]
  [;; Read and write cookies
   wrap-cookies
   ;; Read and write the session cookie
   [wrap-session {:flash true
                  :cookie-attrs cookie-attrs
                  :store session-store}]
   ;; Coerce query-params & form-params
   parameters/parameters-middleware
   ;; Parse multipart data
   multipart/multipart-middleware
   ;; content-negotiation
   muuntaja.middle/format-negotiate-middleware
   ;; Encoding response body
   muuntaja.middle/format-response-middleware
   ;; exception handling
   exception/exception-middleware
   ;; Flash messages in the session
   wrap-flash
   ;; Check CSRF tokens
   wrap-anti-forgery
   ;; decoding request body
   muuntaja.middle/format-request-middleware
   ;; coercing response bodys
   coercion/coerce-response-middleware
   ;; coercing request parameters
   coercion/coerce-request-middleware
   ;; Bind the request globals
   bind-globals-middlware
   ;; Populate the request context
   [wrap-context context]
   ;; Vectors that are returned by handlers will be rendered to html
   render-html-middleware
   ;; Handle exceptions
   exception-middleware])

(defmethod ig/init-key ::app [_ {:keys [routes middleware reload-per-request? print-request-diffs?]}]
  (when (and reload-per-request?
             (or (not (var? routes) )
                 (not (fn? (var-get routes)))))
    (println "WARNING: For :reload-per-request? to work you need to pass a function var for routes."))

  (let [router-options (cond-> {;; Use for pretty exceptions for route
                                ;; definition errors and not exceptions during
                                ;; requests
                                :exception pretty/exception
                                :data {:muuntaja muuntaja/instance
                                       :middleware middleware}}
                         ;; Print out a diff of the request between each
                         ;; middleware. Should only be run in dev mode.
                         print-request-diffs?
                         (assoc :reitit.middleware/transform dev/print-request-diffs))
        ;; Always make routes a function
        routes (cond
                 (var? routes)
                 (if (fn? (var-get routes))
                   routes
                   (constantly (var-get routes)))
                 (fn? routes) routes
                 :else (constantly routes))
        create-handler (fn []
                         (reitit.ring/ring-handler
                           (reitit.ring/router (routes) router-options)
                           (reitit.ring/create-default-handler)))]
    (if reload-per-request?
      (reitit.ring/reloading-ring-handler create-handler)
      (create-handler))))

(defmethod ig/init-key ::jetty [_ {:keys [handler options]}]
  (jetty/run-jetty handler options))

(defmethod ig/halt-key! ::jetty [_ server]
  (when server
    (jetty/stop-server server)))

(def Options
  (mu/optional-keys
   [:map
    [:routes [:or
              [:-> :any [:sequential :any]]
              [:sequential :any]]]
    ;; Extensions are a seq of functions that accept the system config
    ;; map and return a transformed system config map
    [:extensions [:sequential :any]]
    ;; Allow putting things in the ::context of the request. Allow it to be a
    ;; map of keywords to anything.
    [:request-context [:map-of :keyword :any]]
    [:cookie-secret [:or
                     [:string {:min 16 :max 16}]
                     [bytes? {:min 16 :max 16}]]]
    [:jetty [:map-of :keyword :any]]
    ;; The port to connect. If the port is also specified in the :jetty key then
    ;; this :port key will be ignored.
    [:port :int]
    [:reload-per-request?  :boolean]
    [:print-request-diffs? :boolean]
    ;; Start the default jetty. Defaults to true.
    [:start-server? :boolean]]))

(defn options-from-env
  "Return an options map from environment variables.

  The following options can be set from environment variables:
  :print-request-diffs? - ZODIAC_PRINT_REQUEST_DIFFS
  :reload-per-request? - ZODIAC_RELOAD_PER_REQUEST
  "
  []
  {:cookie-secret (System/getenv "ZODIAC_COOKIE_SECRET")
   :port (System/getenv "ZODIAC_PORT")
   :reload-per-request? (System/getenv "ZODIAC_RELOAD_PER_REQUEST")
   :print-request-diffs? (System/getenv "ZODIAC_PRINT_REQUEST_DIFFS")})

(defn start
  ([]
   (start {}))
  ([options]
   (if-not (m/validate Options options)
     (println "WARNING: Invalid options: " (me/humanize (m/explain Options options)))
     (let [config (cond-> {::cookie-store {:secret (:cookie-secret options)}
                           ::middleware {:context (:request-context options {})
                                         :cookie-attrs (:cookie-attrs options {:http-only true})
                                         :session-store (ig/ref ::cookie-store)}
                           ::app {:routes (:routes options [])
                                  :middleware (ig/ref ::middleware)
                                  :reload-per-request? (:reload-per-request? options false)
                                  :print-request-diffs? (:print-request-diffs? options false)}
                           ::jetty {:handler (ig/ref ::app)
                                    :options (merge {:port (or (:port options) 3000)
                                                     :join? false}
                                                    (:jetty options))}}
                    ;; Remove the ::jetty key if start-server? is false
                    (not (:start-server? options true))
                    (dissoc ::jetty))
           ;; Extensions are a seq of functions that accept the system config
           ;; map and return a transformed system config map
           config (reduce #(%2 %1) config (:extensions options []))]

       (ig/load-namespaces config)
       (ig/init config)))))

(defn stop [system]
  (ig/halt! system))
