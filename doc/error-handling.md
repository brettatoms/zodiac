# Error Handling

Zodiac provides a flexible error handling system that lets you customize how exceptions are caught and transformed into HTTP responses.

## Default Behavior

By default, uncaught exceptions return a 500 response with the body `"Unknown error"`. The exception is logged with its stack trace.

## Custom Error Handlers

Use the `:error-handlers` option to define custom handlers. Handlers are keyed by exception class or by the `:type` value in `ex-info` data.

```clojure
(require '[zodiac.core :as z])

(defn my-error-handler [exception request]
  {:status 500
   :body "Something went wrong"})

(z/start {:routes routes
          :error-handlers {Exception my-error-handler}})
```

The handler function receives two arguments:
- `exception` - The thrown exception
- `request` - The Ring request map

### Handling by Exception Class

```clojure
(z/start {:routes routes
          :error-handlers {IllegalArgumentException
                           (fn [e req]
                             {:status 400
                              :body (str "Bad argument: " (.getMessage e))})

                           java.io.IOException
                           (fn [e req]
                             {:status 503
                              :body "Service unavailable"})}})
```

### Handling by ex-info Type

For exceptions created with `ex-info`, you can key handlers by the `:type` in the exception data:

```clojure
(defn handler [request]
  (throw (ex-info "Not found" {:type ::not-found
                               :id (:id (:path-params request))})))

(z/start {:routes routes
          :error-handlers {::not-found
                           (fn [e req]
                             {:status 404
                              :body (str "Resource not found: "
                                         (:id (ex-data e)))})}})
```

## Returning Different Response Types

Error handlers can return any valid Ring response, including HTML or JSON:

```clojure
;; HTML error response
(z/start {:routes routes
          :error-handlers {::not-found
                           (fn [e req]
                             (z/html-response 404
                               [:div
                                [:h1 "Not Found"]
                                [:p "The requested resource does not exist."]]))}})

;; JSON error response
(z/start {:routes routes
          :error-handlers {::api-error
                           (fn [e req]
                             (z/json-response 500
                               {:error "Internal error"
                                :code (:code (ex-data e))}))}})
```

## Coercion Errors

Reitit coercion errors (from parameter validation) can be handled using the `::coercion/request-coercion` key:

```clojure
(require '[reitit.coercion :as coercion])

(z/start {:routes routes
          :error-handlers {::coercion/request-coercion
                           (fn [e req]
                             (z/json-response 400
                               {:error "Validation failed"
                                :details (ex-data e)}))}})
```

See [Routing](routing.md) for more on parameter coercion.

## Handler Priority

When multiple handlers could match an exception:
1. Exact `:type` match (for `ex-info`) takes precedence
2. Most specific exception class wins
3. Falls back to default handler if no match
