# Zodiac

Zodiac is a small web framework for Clojure that provides a reasonable set of defaults while also being easily extensible.  Zodiac stands on the shoulders of giants rather than being innovative.  What makes Zodiac different than other Clojure web frameworks is that the parts or put together in an elegant way that makes it both quick and easy to get started but endlessly extensible.

At its core Zodiac is built on [ring](https://github.com/ring-clojure/ring), [reitit](https://github.com/metosin/reitit) and [integrant](https://github.com/weavejester/integrant).

### Getting started

``` clojure
(ns myapp
  (:require [zodiac.core :as z]))

(defn routes []
  ;; routes use the reitit route syntax
  ["/" {:handler (constantly {:status 200
                              :body "ok"})}])

(z/start {:routes #'routes})
```

### Options

The `zodiac.core/start` function takes a single options map with the following keys:

- `:routes`: The route definition using the reitit route syntax
- `:extensions`: A sequence of functions that accept an integrant system configuration map and return a modified integrant system configuration app.
- `:request-context`: A map of values values to add to the `::z/context` map in the request map.
- `:cookie-secret`: The secret used to encrypt the cookie
- `:jetty`: A map of options to pass to the embedded [ring-jetty9-adapter](https://github.com/sunng87/ring-jetty9-adapter)
- `:port`: The port to listen for connections.  If the port is also specificed in the `:jetty` map then this value will be ignored.  The default is `3000`.
- `:reload-per-request?`: Reload the routes on every request. For this to work you will need to pass the var of the routes function, e.g. `#'routes`.
- `:print-request-diffs?`: Print a diff of each request between each middleware.
- `:start-server?`: Set to `false` to disable the embedded jetty server.

### Render HTML

Return a vector from the response handler to  automatically convert the vector to an html response.

``` clojure
(ns myapp
  (:require [zodiac.core :as z]))

(defn routes []
  ;; Returns a text/html response with <div>hi</div> for the body.
  ["/" {:handler (fn [_] [:div "hi"] )}])

(z/start {:routes #'routes})
```

### Render JSON

Return a vector from the response handler to  automatically convert the vector to an html response.

``` clojure
(ns myapp
  (:require [zodiac.core :as z]))

(defn routes []
  ;; Returns an application/json response with {"hello": "world"} for the body.
  ["/" {:handler (fn [_] (z/json-response {:hello "world"}))}])

(z/start {:routes #'routes})
```

### Extending

Zodiac can be extended using a sequence of functions that take an integrant system map and return a modified integrant system map.

``` clojure
(defn service-ext [cfg]
  (-> cfg
      ;; Add a ::service component to the config map
      (assoc ::service {:value "hi"})
      ;; Put an instance of the service in the request context
      (assoc-in [::z/middleware :context :service] (ig/ref ::service))))

(defn routes []
  ;; routes use the reitit route syntax
  ["/" {:handler (fn [{:keys [::z/context]}]
                   {:status 200
                    :body (-> context :service :value)})}])

(z/start {:routes #'routes
          :extensions [service-ext]})
```


### Acknowledgements

Zodiac wouldn't be possible if not for the following people:

- James Reeves ([weavejest](https://github.com/weavejester)): Ring, integrant, cljfmt, etc
- [Metosin](https://github.com/metosin): [reitit](https://github.com/metosin/reitit), [malli](https://github.com/metosin/mallireitit), etc
