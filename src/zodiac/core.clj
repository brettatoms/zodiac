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
            [ring.adapter.jetty :as jetty]
            [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
            [ring.middleware.anti-forgery.session :as anti-forgery.session]
            [ring.middleware.anti-forgery.strategy :as anti-forgery.strategy]
            [ring.middleware.cookies :refer [wrap-cookies]]
            [ring.middleware.flash :refer [wrap-flash]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.session.cookie :refer [cookie-store]]))

;; These variables are bound to the current request.

(def ^:dynamic *request*
  "Bound to the current request."
  nil)

(def ^:dynamic *router*
  "The router used by the current request.  This is the same as `(:reitit.core/router *request*)`"
  nil)

(def ^:dynamic *session*
  "The session in the current request.  This is the same as `(:session *request*)`"
  nil)

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
  "Return a url string given a route name and optional arg and query params. This
  functions uses the router for the current request bound to *request*.

  If you need to use a different router then temporary bind *router* before
  calling this function."
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

(defn- context-middleware [handler context]
  (fn [request]
    (-> request
        (assoc ::context context)
        (handler))))

(defn- render-html-middleware [handler]
  (fn [request]
    (let [response (handler request)]
      (if (vector? response)
        (html-response response)
        response))))

(defn- bind-globals-middleware [handler]
  (fn [{:keys [session ::r/router] :as request}]
    (binding [*request* request
              *router* router
              *session* session]
      (handler request))))

(defmethod ig/init-key ::cookie-store [_ {:keys [secret]}]
  (cookie-store {:key (cond
                        (bytes? secret) secret
                        (string? secret) (.getBytes secret))}))

(defn- create-exception-middleware
  ([]
   (create-exception-middleware {}))
  ([custom-handlers]
   (exception/create-exception-middleware
    (merge exception/default-handlers
           {Exception (fn [exception _request]
                        (log/error exception)
                        {:status 500
                         :body "Unknown error"})

            ;; print stack-traces for all exceptions
            ::exception/wrap (fn [handler e request]
                               (log/error "ERROR: " (pr-str (:uri request)))
                               (log/error e)
                               (handler e request))}
           custom-handlers))))

(defmethod ig/init-key ::anti-forgery-config [_ {:keys [whitelist]}]
  (let [session-strategy (anti-forgery.session/session-strategy)]
    {:read-token (fn [{:keys [uri headers form-params multipart-params] :as request}]
                   ;; Duplicates ring.middleware.anti-forgery/default-request-token but
                   ;; also considers a uri that matches the whitelist a valid request
                   (let [params (merge form-params multipart-params)
                         uri-matches? (fn [uri pattern] (re-matches (re-pattern pattern) uri))]
                     (or (some? (some #(uri-matches? uri %) whitelist))
                         (get params "__anti-forgery-token")
                         (get headers "x-csrf-token")
                         (get headers "x-xsrf-token"))))
     ;; An anti-forgery strategy that wraps the session strategy but also allows
     ;; check if read-token returns a true value for the token, i.e. the uri
     ;; matched a path in the whitelist
     :strategy (reify anti-forgery.strategy/Strategy
                 (valid-token? [_ request token]
                   ;; If read-token returned true for the token value then
                   ;; assume its a valid token
                   (or (true? token)
                       (.valid-token? session-strategy request token)))
                 (get-token [_ request]
                   (.get-token session-strategy request))
                 (write-token [_ request response token]
                   (.write-token session-strategy request response token)))}))

(defmethod ig/init-key ::middleware
  [_ {:keys [context cookie-attrs error-handlers extra session-store anti-forgery-config]}]
  (into (or extra [])
        [ ;; Read and write cookies
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
         ;; Handle exceptions
         (create-exception-middleware error-handlers)
         ;; Flash messages in the session
         wrap-flash
         ;; Check CSRF tokens
         [wrap-anti-forgery anti-forgery-config]
         ;; decoding request body
         muuntaja.middle/format-request-middleware
         ;; coercing response bodys
         coercion/coerce-response-middleware
         ;; coercing request parameters
         coercion/coerce-request-middleware
         ;; coerce exceptions
         coercion/coerce-exceptions-middleware
         ;; Populate the request context
         [context-middleware context]
         ;; Bind the request globals
         bind-globals-middleware
         ;; Vectors that are returned by handlers will be rendered to html
         render-html-middleware]))

(defmethod ig/init-key ::default-handler [_ _]
  (reitit.ring/create-default-handler))

(defmethod ig/init-key ::app [_ {:keys [router default-handlers reload-per-request?]}]
  (let [router-factory (if (fn? router)
                         router
                         (constantly router))
        create-handler (fn []
                         (reitit.ring/ring-handler
                          (router-factory)
                          (apply reitit.ring/routes default-handlers)))]
    (if reload-per-request?
      (reitit.ring/reloading-ring-handler create-handler)
      (create-handler))))

;; The ::router component returns a router factory so that the if
;; reload-per-request? is true then the full route definition gets rebuilt on
;; every request
(defmethod ig/init-key ::router [_ {:keys [routes middleware print-request-diffs? reload-per-request?]}]
  (when (and reload-per-request?
             (or (not (var? routes))
                 (not (fn? (var-get routes)))))
    (log/warn "WARNING: For :reload-per-request? to work you need to pass a function var for routes."))
  (let [router-options (cond-> {;; Use for pretty exceptions for route
                                ;; definition errors and not exceptions during
                                ;; requests
                                :exception pretty/exception
                                :data {:muuntaja muuntaja/instance
                                       :middleware middleware
                                       :coercion reitit.coercion.malli/coercion}}
                         ;; Print out a diff of the request between each
                         ;; middleware. Should only be run in dev mode.
                         print-request-diffs?
                         (assoc :reitit.middleware/transform dev/print-request-diffs))
        routes (cond
                 (var? routes) (if (fn? (var-get routes))
                                 routes
                                 (constantly (var-get routes)))
                 (fn? routes) routes
                 :else (constantly routes))]
    (fn []
      (reitit.ring/router (routes) router-options))))

(defmethod ig/init-key ::jetty [_ {:keys [handler options]}]
  (log/debug "Starting zodiac.core/jetty...\n")
  (jetty/run-jetty handler options))

(defmethod ig/halt-key! ::jetty [_ server]
  (log/debug "Stopping zodiac.core/jetty...\n")
  (when server
    (.stop server)))

(def ^:private Options
  (mu/optional-keys
   [:map
    [:routes [:or
              [:-> :any [:sequential :any]]
              [:sequential :any]]]
    ;; Extensions are a seq of functions that accept the system config
    ;; map and return a transformed system config map
    [:extensions [:sequential :any]]
    ;; Add keys to the request context.
    [:request-context [:map-of :keyword :any]]
    [:cookie-secret [:or
                     [:string {:min 16 :max 16}]
                     [bytes? {:min 16 :max 16}]]]
    [:cookie-attrs [:map-of :keyword :any]]
    [:jetty [:map-of :keyword :any]]
    ;; The port to connect. If the port is also specified in the :jetty key then
    ;; this :port key will be ignored.
    [:port :int]
    [:reload-per-request?  :boolean]
    [:print-request-diffs? :boolean]
    ;; Start the default jetty. Defaults to true.
    [:start-server? :boolean]
    [:error-handlers :any]
    [:anti-forgery-whitelist [:sequential [:or
                                           :string
                                           [:fn #(instance? java.util.regex.Pattern %)]]]]]))
(defn start
  "Start the zodiac server.  Returns an integrant system map."
  ([]
   (start {}))
  ([options]
   (if-not (m/validate Options options)
     (log/warn "WARNING: Invalid options: " (me/humanize (m/explain Options options)))
     (let [config (cond-> {::cookie-store {:secret (:cookie-secret options)}
                           ::anti-forgery-config {:whitelist (:anti-forgery-whitelist options [])}
                           ::middleware {:extra (:middleware options)
                                         :context (:request-context options {})
                                         :cookie-attrs (:cookie-attrs options {:http-only true
                                                                               :same-site :lax})
                                         :session-store (ig/ref ::cookie-store)
                                         :error-handlers (:error-handlers options {})
                                         :anti-forgery-config (ig/ref ::anti-forgery-config)}
                           ::router {:routes (:routes options [])
                                     :middleware (ig/ref ::middleware)
                                     :reload-per-request? (:reload-per-request? options false)
                                     :print-request-diffs? (:print-request-diffs? options false)}
                           ::default-handler {}
                           ::app {:router (ig/ref ::router)
                                  :default-handlers [(ig/ref ::default-handler)]
                                  :reload-per-request? (:reload-per-request? options false)}
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

       (try
         (ig/init config)
         (catch clojure.lang.ExceptionInfo e
           (log/error e)
           ;; Roll back system start if there's an error
           (when-let [system (-> e ex-data :system)]
             (ig/halt! system))

           (throw (ex-info (str "Error starting Zodiac: " (ex-message e))
                           (ex-data e)
                           e))))))))

(defn stop
  "Stop the zodiac server.  Accepts the system map returned"
  [system]
  (ig/halt! system))
