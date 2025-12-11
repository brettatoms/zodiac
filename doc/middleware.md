# Middleware

Zodiac comes with a preconfigured middleware stack. You can also add custom middleware at the application or route level.

## Default Middleware Stack

Middleware executes in this order (outermost to innermost for requests, reversed for responses):

1. **Cookies** - Parses and serializes cookies
2. **Session** - Cookie-based session management
3. **Parameters** - Parses query string and form parameters
4. **Multipart** - Handles multipart form data (file uploads)
5. **Content Negotiation** - Determines response format from Accept header
6. **Response Encoding** - Encodes response body based on content type
7. **Exception Handling** - Catches exceptions and converts to responses
8. **Flash** - Manages flash messages
9. **Anti-Forgery (CSRF)** - Validates CSRF tokens on non-GET requests
10. **Request Body Decoding** - Parses JSON request bodies
11. **Response Coercion** - Validates response bodies against schemas
12. **Request Coercion** - Validates and coerces request parameters
13. **Coercion Exceptions** - Formats coercion errors
14. **Context Injection** - Adds `::z/context` to request
15. **Globals Binding** - Binds `*request*`, `*router*`, `*session*`
16. **HTML Rendering** - Converts vector responses to HTML

## Adding Custom Middleware

### Application-Level

Use the `:middleware` option to add middleware that wraps all routes:

```clojure
(defn timing-middleware [handler]
  (fn [request]
    (let [start (System/currentTimeMillis)
          response (handler request)
          elapsed (- (System/currentTimeMillis) start)]
      (assoc-in response [:headers "X-Response-Time"]
                (str elapsed "ms")))))

(z/start {:routes routes
          :middleware [timing-middleware]})
```

Your middleware wraps (runs outside of) the default stack.

### Route-Level

Add middleware to specific routes using Reitit's `:middleware` key:

```clojure
(defn auth-middleware [handler]
  (fn [{:keys [session] :as request}]
    (if (:user-id session)
      (handler request)
      {:status 401 :body "Unauthorized"})))

(def routes
  [""
   ["/" {:get public-handler}]
   ["/admin" {:middleware [auth-middleware]
              :get admin-handler}]
   ["/api" {:middleware [auth-middleware]}
    ["/users" {:get users-handler}]
    ["/items" {:get items-handler}]]])
```

Route middleware runs inside the application middleware.

## Middleware Execution Order

For a request flowing through middleware A -> B -> C -> Handler:

```
Request:  A -> B -> C -> Handler
Response: A <- B <- C <- Handler
```

In code terms:

```clojure
;; Middleware A wraps B wraps C wraps handler
(def app (A (B (C handler))))

;; A request goes: A's pre-processing -> B's pre-processing -> C's pre-processing -> handler
;; Response goes:  A's post-processing <- B's post-processing <- C's post-processing <- handler
```

## CSRF Protection

By default, POST/PUT/DELETE requests require a valid CSRF token. To whitelist routes:

```clojure
(z/start {:routes routes
          :anti-forgery-whitelist ["/api/webhook"      ;; exact path
                                   #"/api/public/.*"]}) ;; regex pattern
```

Access the current token via `ring.middleware.anti-forgery/*anti-forgery-token*`:

```clojure
(require '[ring.middleware.anti-forgery :refer [*anti-forgery-token*]])

(defn form []
  [:form {:method "post"}
   [:input {:type "hidden"
            :name "__anti-forgery-token"
            :value *anti-forgery-token*}]
   ...])
```

Or send it in the `X-CSRF-Token` header for AJAX requests.
