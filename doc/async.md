# Async Requests

Zodiac can run in asynchronous mode, which lets handlers hold a connection open
without occupying a request worker thread. This enables long-lived connections
such as Server-Sent Events (SSE) and streaming responses.

Async support is built on Ring's [3-arity handler
contract](https://github.com/ring-clojure/ring/wiki/Concepts#handlers). A
synchronous handler takes one argument and returns a response:

```clojure
(fn [request] {:status 200 :body "hello"})
```

An asynchronous handler takes three arguments and calls `respond` with the
response (or `raise` with an exception) rather than returning:

```clojure
(fn [request respond raise]
  (respond {:status 200 :body "hello"}))
```

## Enabling Async Mode

Set the top-level `:async?` option to `true`:

```clojure
(z/start {:routes routes
          :async? true})
```

This does two things:

- Runs the bundled Jetty adapter in async mode.
- Installs an adapter so synchronous and asynchronous handlers can coexist.

You do not need to set `:jetty {:async? true}` yourself — the `:async?` option
does it for you. Any options you pass under `:jetty` are still forwarded to the
adapter, so you can tune async behavior, for example the async timeout:

```clojure
(z/start {:routes routes
          :async? true
          ;; 0 = no timeout; keep long-lived connections open
          :jetty {:async-timeout 0}})
```

## Handlers Are Synchronous by Default

When the server runs in async mode, Jetty invokes **every** handler with three
arguments. Ordinary Ring and Reitit do not upgrade a synchronous handler's
arity, so a plain `(fn [request] ...)` handler would normally throw an
`ArityException`.

Zodiac handles this for you. Handlers are treated as synchronous by default: a
handler is called with a single argument and its return value becomes the
response. A route opts into the asynchronous contract with `:zodiac/async? true`
in its route data:

```clojure
(def routes
  [""
   ;; Synchronous handler (the default) — no changes needed in async mode.
   ["/" {:handler index-handler}]
   ;; Asynchronous handler — receives (request respond raise).
   ["/events" {:handler events-handler
               :zodiac/async? true}]])
```

You can also write the marker as `:zodiac.core/async?`, which is convenient when
`zodiac.core` is aliased to `z`:

```clojure
(require '[zodiac.core :as z])

["/events" {:handler events-handler
            ::z/async? true}]
```

Deciding sync vs. async per route (rather than inspecting the handler at
runtime) is deliberate: handlers wrapped with `constantly`, `comp`, or `partial`
cannot be reliably introspected, so an explicit marker is the only correct
signal.

## Exception Handling

Errors raised via the `raise` callback flow through Zodiac's exception
middleware exactly as thrown exceptions do for synchronous handlers. A raised
exception is turned into a response (by default a `500`), not propagated to the
server. Custom `:error-handlers` apply to both sync and async handlers.

## Dynamic Vars in Async Handlers

The `*request*`, `*router*`, and `*session*` dynamic vars are bound around the
handler invocation. In an async handler, work often continues on a different
thread after the handler returns (for example inside a `future` or a streaming
body), where those thread-local bindings do not apply.

In async handlers, read from the request map directly instead:

```clojure
(fn [request respond raise]
  (let [session (:session request)
        router  (:reitit.core/router request)]
    (respond ...)))
```

`z/url-for` relies on `*router*`; if you need it off the handler thread, capture
the router from the request and bind `*router*` yourself, or pass the router
explicitly.

## Middleware Must Be Async-Compatible

In async mode, every middleware in the stack is invoked with the 3-arity form.
Zodiac's built-in middleware already supports this. **Any custom or third-party
middleware you add must also implement the 3-arity (`[request respond raise]`)
form** — this is a Ring-wide requirement that Zodiac cannot paper over. A
synchronous-only middleware in the chain will throw an `ArityException` when the
server runs in async mode.

A middleware that supports both looks like:

```clojure
(defn my-middleware [handler]
  (fn
    ([request]
     (handler (assoc request :x 1)))
    ([request respond raise]
     (handler (assoc request :x 1) respond raise))))
```

## Streaming Responses (SSE Example)

A long-lived streaming response uses a body that implements
`ring.core.protocols/StreamableResponseBody`. The handler responds immediately
and the body is written incrementally, holding the connection open. See the
runnable example in `examples/async-sse/`.

```clojure
(require '[ring.core.protocols :as ring.protocols])

(defn sse-body [n]
  (reify ring.protocols/StreamableResponseBody
    (write-body-to-stream [_ _response out]
      (try
        (dotimes [_ n]
          (.write out (.getBytes (format "data: %s\n\n" (java.time.Instant/now)) "UTF-8"))
          (.flush out)
          (Thread/sleep 1000))
        (finally
          (.close out))))))

(defn events-handler [_request respond _raise]
  (respond {:status 200
            :headers {"content-type" "text/event-stream"
                      "cache-control" "no-cache"}
            :body (sse-body 10)}))

(def routes
  [["/events" {:handler events-handler
               :zodiac/async? true
               ;; SSE is a GET the browser can't attach a CSRF token to.
               :zodiac/skip-csrf true}]])
```

## Streaming and Threads

It is worth being precise about what async mode does and does not change for a
long-lived or large response (a big file download, an SSE stream, etc.).

The response body is written to the client with a **blocking** write. Ring's
Jetty adapter writes the body by calling
`ring.core.protocols/write-body-to-stream` synchronously against a blocking
output stream — it does not use the Servlet non-blocking `WriteListener` API.
This is true in both synchronous and asynchronous mode. So streaming always
occupies a thread for the full duration of the write.

What async mode changes is **which** thread is occupied:

- **Synchronous mode:** the handler runs and the body is written on the Jetty
  request-worker thread. That worker is held for the entire stream. A slow
  client or a long-lived stream ties up one of Jetty's limited request threads
  (50 by default) for its whole lifetime.
- **Asynchronous mode:** the handler returns immediately and the Jetty request
  worker is released. The body write happens later, on whatever thread calls
  `respond`. If you call `respond` from a `future` or your own executor, the
  blocking write occupies a thread from *that* pool, not Jetty's request pool.

In other words, async mode does not make the write non-blocking — it lets you
move the blocking write off Jetty's request-worker pool so slow clients and
long streams don't exhaust it.

### Making the blocking write cheap with virtual threads

On JDK 21+ the simplest way to scale blocking streams is to run them on virtual
threads, where a parked (blocked-on-I/O) thread costs almost nothing. Zodiac
passes `:jetty` options straight through to the Ring Jetty adapter, so you can
hand Jetty a thread pool backed by a virtual-thread executor without any
framework-specific option:

```clojure
(import '[org.eclipse.jetty.util.thread QueuedThreadPool]
        '[java.util.concurrent Executors])

(z/start
 {:routes routes
  :async? true
  :jetty {:thread-pool
          (doto (QueuedThreadPool.)
            (.setVirtualThreadsExecutor
             (Executors/newVirtualThreadPerTaskExecutor)))}})
```

With this, a long download or stream parks a cheap virtual thread instead of a
platform request worker, so the server can hold a very large number of
concurrent slow connections. This is a Jetty configuration concern rather than a
Zodiac feature — Zodiac stays a preconfigured Ring app and lets the adapter own
the threading model.
