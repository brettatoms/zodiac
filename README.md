# Zodiac

[![Clojars Project](https://img.shields.io/clojars/v/com.github.brettatoms/zodiac.svg)](https://clojars.org/com.github.brettatoms/zodiac)

Come chat in [#zodiac](https://clojurians.slack.com/archives/C07V6RVUN7J)

Zodiac is a small web framework for Clojure that provides a reasonable set of defaults while also being easily extensible.  Zodiac stands on the shoulders of giants rather than being innovative.  At its core Zodiac is mostly just a preconfigured Ring app and not more that a few hundred lines of code.

Zodiac tries to fill a similar niche as the [Flask](https://flask.palletsprojects.com) framework with defaults that make it quick to start a new Clojure based web app without being heavy-handed.

What Zodiac includes by default:

 - Routing and middleware.  We use [Reitit](https://github.com/metosin/reitit)
 - Request and response handing with [Ring](https://github.com/ring-clojure/ring).
 - A jetty server (though Jetty can be turned off)
 - Automatic Hiccup-based HTML rendering using [Chassis](https://github.com/onionpancakes/chassis).
 - Websocket support
 - File streaming
 - Flash messages
 - Cookies and secure session handler
 - Form and JSON request parsing
 - Extensible. Pass a list of functions to extend Zodiac.  Override the error handlers.
 - Convenience
   - Helpers to lookup routes
   - Helpers to return hiccup and JSON responses
   - A request context
   - Variables dynamically bound to the current request, router and session

 What Zodiac doesn't do:
 - Dictate a file structure with generators or scaffolding.
 - No configuration over code
 - No path based routing, etc.
 - Expect a certain database - (see [Zodiac SQL](https://github.com/brettatoms/zodiac-sql))
 - Asset bundling (see [Zodiac Assets](https://github.com/brettatoms/zodiac-assets))

And that's about it.  Zodiac is mostly feature complete although you can expect plenty of bug fixes and polish before a 1.0.x release.  Additional features like common database setup and asset handling will be done as Zodiac extensions.

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
- `:cookie-attrs`: Override the code settings.  Defaults to `{:http-only true :same-site :lax}`.
- `:jetty`: A map of options to pass to the embedded [ring-jetty9-adapter](https://github.com/sunng87/ring-jetty9-adapter)
- `:port`: The port to listen for connections.  If the port is also specified in the `:jetty` map then this value will be ignored.  The default is `3000`.
- `:error-handlers`: A map of types to error handler functions
- `:anti-forgery-whitelist`: A sequence or strings or regular expressions to bypass anti-forgery/csrf checking for matching routes.
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
  ["/" {:handler (fn [_] [:div "hi"])}])

(z/start {:routes #'routes})
```

### Render JSON

Use the `zodiac.core/json-response` function to encode Clojure maps to JSON and return `application/json` HTTP responses.

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

### Extensions

- [Zodiac Assets](https://github.com/brettatoms/zodiac-assets): Static asset
  building and url lookup with [Vite](https://vite.dev/).

- [Zodiac SQL](https://github.com/brettatoms/zodiac-sql): Helper for connecting
  to SQL database and running
  [HoneySQL](https://github.com/seancorfield/honeysql) queries.
