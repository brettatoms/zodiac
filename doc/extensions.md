# Extensions

Extensions let you add components to Zodiac's [Integrant](https://github.com/weavejester/integrant) system and inject them into the request context.

## What is an Extension?

An extension is a function that takes an Integrant configuration map and returns a modified configuration:

```clojure
(fn [config] -> config)
```

Extensions are applied in order before the system starts.

## Basic Example

Add a value to the request context:

```clojure
(require '[zodiac.core :as z])

(defn my-extension [config]
  (assoc-in config [::z/middleware :context :app-name] "My App"))

(def routes
  ["/" {:get (fn [{::z/keys [context]}]
               {:status 200
                :body (str "Welcome to " (:app-name context))})}])

(z/start {:routes routes
          :extensions [my-extension]})
```

## Adding Integrant Components

For stateful resources (database connections, caches, etc.), define an Integrant component:

```clojure
(require '[integrant.core :as ig])

;; Define how to initialize your component
(defmethod ig/init-key ::database [_ {:keys [url]}]
  (create-connection url))

;; Optional: define cleanup
(defmethod ig/halt-key! ::database [_ conn]
  (close-connection conn))

(defn database-extension [config]
  (-> config
      ;; Add the component to the system
      (assoc ::database {:url "jdbc:postgresql://localhost/mydb"})
      ;; Inject into request context
      (assoc-in [::z/middleware :context :db] (ig/ref ::database))))

(z/start {:routes routes
          :extensions [database-extension]})
```

Now handlers can access `:db` from the context:

```clojure
(defn handler [{::z/keys [context]}]
  (let [db (:db context)
        users (query db "SELECT * FROM users")]
    (z/json-response users)))
```

## Extension Order

Extensions are applied in sequence. Later extensions can override earlier ones:

```clojure
(defn ext1 [config]
  (assoc-in config [::z/middleware :context :value] "first"))

(defn ext2 [config]
  (assoc-in config [::z/middleware :context :value] "second"))

(z/start {:routes routes
          :extensions [ext1 ext2]})
;; :value will be "second"
```

## Real-World Extensions

### Zodiac SQL

[Zodiac SQL](https://github.com/brettatoms/zodiac-sql) provides database connectivity:

```clojure
(require '[zodiac.ext.sql :as sql])

(z/start {:routes routes
          :extensions [(sql/extension {:dbtype "postgresql"
                                       :dbname "myapp"
                                       :host "localhost"})]})

;; In handlers:
(defn handler [{::z/keys [context]}]
  (let [users (sql/query (:db context) ["SELECT * FROM users"])]
    (z/json-response users)))
```

### Zodiac Assets

[Zodiac Assets](https://github.com/brettatoms/zodiac-assets) integrates with [Vite](https://vite.dev/) for asset bundling:

```clojure
(require '[zodiac.ext.assets :as assets])

(z/start {:routes routes
          :extensions [(assets/extension {:manifest-path "public/.vite/manifest.json"})]})

;; In templates:
(defn layout [{::z/keys [context]} & body]
  [:html
   [:head
    [:link {:rel "stylesheet" :href (assets/url context "src/main.css")}]]
   [:body
    body
    [:script {:src (assets/url context "src/main.js")}]]])
```

## Accessing Integrant Config

The full Integrant system is available if needed:

```clojure
(let [system (z/start {:routes routes})]
  ;; system is the initialized Integrant system map
  (::z/app system)     ;; Ring handler
  (::z/router system)  ;; Reitit router

  ;; Stop the system
  (z/stop system))
```
