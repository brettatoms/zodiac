# Request & Response

## Request Map

Zodiac builds on Ring's request map. Your handlers receive a standard Ring request with additional keys:

| Key | Description |
|-----|-------------|
| `:query-params` | Parsed query string parameters |
| `:form-params` | Parsed form body parameters |
| `:body-params` | Parsed JSON body (when Content-Type is application/json) |
| `:path-params` | Route path parameters (e.g., `/:id` -> `{:id "123"}`) |
| `:parameters` | Coerced parameters (when using Malli schemas) |
| `:session` | Session data |
| `:flash` | Flash message from previous request |
| `:cookies` | Request cookies |
| `::z/context` | Values from `:request-context` option and extensions |

### Parameter Access

```clojure
(defn handler [{:keys [query-params form-params body-params path-params]}]
  ;; Query string: /search?q=clojure
  (get query-params "q")  ;; => "clojure"

  ;; Form POST
  (get form-params "email")

  ;; JSON body: {"name": "Alice"}
  (:name body-params)  ;; => "Alice"

  ;; Path params: /users/:id
  (:id path-params))  ;; => "123" (string)
```

### Coerced Parameters

When you define `:parameters` on a route, coerced values appear under `:parameters`:

```clojure
(def routes
  ["/users/:id" {:get handler
                 :parameters {:path {:id int?}
                              :query {:active boolean?}}}])

(defn handler [{:keys [parameters]}]
  (let [{:keys [id]} (:path parameters)      ;; int, not string
        {:keys [active]} (:query parameters)] ;; boolean
    ...))
```

See [Routing](routing.md) for more on coercion.

## Dynamic Variables

Three dynamic vars are bound during request handling:

```clojure
(require '[zodiac.core :as z])

z/*request*  ;; Current request map
z/*router*   ;; Reitit router (for url-for)
z/*session*  ;; Current session data
```

These are useful in helper functions that don't have direct access to the request:

```clojure
(defn current-user []
  (:user-id z/*session*))

(defn build-nav []
  [:nav
   [:a {:href (z/url-for :home)} "Home"]
   [:a {:href (z/url-for :profile)} "Profile"]])
```

## Response Formats

### Standard Ring Response

```clojure
(defn handler [request]
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body "Hello"})
```

### Vector (Auto-HTML)

Return a vector to automatically render HTML via [Chassis](https://github.com/onionpancakes/chassis):

```clojure
(defn handler [request]
  [:div
   [:h1 "Hello"]
   [:p "This becomes HTML"]])
;; => {:status 200, :headers {"content-type" "text/html"}, :body "<!DOCTYPE html><div>..."}
```

### html-response

Explicit HTML response with optional status:

```clojure
(z/html-response [:div "Hello"])           ;; 200
(z/html-response 201 [:div "Created"])     ;; 201
(z/html-response "raw html string")        ;; String passed through
```

### json-response

JSON response with optional status:

```clojure
(z/json-response {:message "Hello"})       ;; 200
(z/json-response 201 {:id 123})            ;; 201
```

## URL Generation

Use `url-for` to generate URLs from route names:

```clojure
(def routes
  [""
   ["/" {:name :home :get home-handler}]
   ["/users/:id" {:name :user :get user-handler}]])

;; In a handler (where *router* is bound):
(z/url-for :home)                    ;; => "/"
(z/url-for :user {:id 42})           ;; => "/users/42"
(z/url-for :user {:id 42} {:tab "settings"})  ;; => "/users/42?tab=settings"
```

## Cookies

### Setting Cookies

```clojure
(defn handler [request]
  {:status 200
   :body "OK"
   :cookies {"preference" {:value "dark-mode"
                           :max-age 86400
                           :path "/"
                           :http-only true
                           :same-site :strict}}})
```

### Reading Cookies

```clojure
(defn handler [{:keys [cookies]}]
  (let [pref (get-in cookies ["preference" :value])]
    ...))
```
