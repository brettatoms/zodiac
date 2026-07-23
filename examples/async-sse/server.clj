(ns server
  (:require [dev.onionpancakes.chassis.core :as c]
            [integrant.core :as ig]
            [ring.core.protocols :as ring.protocols]
            [taoensso.telemere.tools-logging :as tt]
            [zodiac.core :as z])
  (:import [java.io OutputStream])
  (:gen-class))

(tt/tools-logging->telemere!) ;; send tools.logging to telemere
(add-tap println)

(defonce ^:dynamic *system* nil)

(defn index-handler
  "A regular synchronous handler that renders the page. Sync and async handlers
  coexist in the same app."
  [_request]
  [:div
   [:h1 "Server-Sent Events example"]
   [:p "The server pushes the time once a second over a long-lived connection."]
   [:pre#events ""]
   [:script (c/raw "
  var source = new EventSource('/events');
  source.onmessage = (event) => {
    var pre = document.getElementById('events');
    pre.textContent = event.data + '\\n' + pre.textContent;
  };
")]])

(defn sse-body
  "A streamable response body that pushes an SSE message once a second. This runs
  after the async handler has already returned, on whatever thread Jetty uses to
  write the response, holding the connection open without occupying a request
  worker thread."
  [count]
  (reify ring.protocols/StreamableResponseBody
    (write-body-to-stream [_ _response out]
      (let [^OutputStream out out]
        (try
          (dotimes [_ count]
            (let [event (format "data: %s\n\n" (java.time.Instant/now))]
              (.write out (.getBytes event "UTF-8"))
              (.flush out))
            (Thread/sleep 1000))
          (finally
            (.close out)))))))

(defn events-handler
  "An async (3-arity) handler. It responds immediately with a streaming body and
  lets Jetty drive the long-lived connection.

  A route opts into the async handler contract with :zodiac/async? true (see
  routes below). Note: in async handlers prefer reading from the request map
  directly (e.g. (:session request)) rather than the *request*/*session* dynamic
  vars, since work may continue on a different thread after the handler returns."
  [_request respond _raise]
  (respond {:status 200
            :headers {"content-type" "text/event-stream"
                      "cache-control" "no-cache"}
            :body (sse-body 10)}))

(defn routes []
  [""
   ;; A plain synchronous handler. Sync and async handlers coexist even when the
   ;; server runs in async mode.
   ["/" {:handler index-handler}]
   ["/events" {:handler events-handler
               ;; Opt this route into the async (3-arity) handler contract.
               :zodiac/async? true
               ;; SSE is a GET the browser can't attach a CSRF token to.
               :zodiac/skip-csrf true}]])

(defn -main [& _]
  (let [sys (z/start {:routes #'routes
                      :reload-per-request? true
                      ;; Run the server in async mode. This enables Jetty's async
                      ;; mode and installs the adapter that lets sync and async
                      ;; handlers coexist.
                      :async? true
                      ;; 0 = no timeout; keep the SSE connection open. Zodiac
                      ;; passes :jetty options straight through to the adapter.
                      :jetty {:async-timeout 0}})]
    ;; Assign the system to a dynamic var so we can stop it later
    (alter-var-root #'*system* (constantly sys))
    (println "Ready to go on http://localhost:3000 ...")))

(comment
  (-main)

  (ig/halt! *system*)

  (do
    (when *system*
      (ig/halt! *system*))
    (-main))
  ())
