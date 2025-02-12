# Zodiac: A Minimalist Clojure Web Framework Focused on Flexibility and Developer Freedom

[![Clojars Project](https://img.shields.io/clojars/v/com.github.brettatoms/zodiac.svg)](https://clojars.org/com.github.brettatoms/zodiac)

Come chat in [#zodiac](https://clojurians.slack.com/archives/C07V6RVUN7J)

# Why Zodiac?

Clojure's web ecosystem offers incredible power through modular libraries, but starting a new project often means:
- Endless decisions about routing, middleware, and templating
- Boilerplate wiring of disparate components
- Either too much magic or not enough structure

**Zodiac offers a third way** - a curated foundation that handles web fundamentals while leaving *you* in control. Think of it as **Flask for Clojure** - a lightweight framework that:
- 🛠️ Provides essential web toolkit out of the box
- 🧩 Lets you choose your own libraries for databases, auth, etc.
- 🚀 Gets you from zero to production fast
- 🔧 Grows gracefully from prototype to complex app

```clojure
(ns myapp
  (:require [zodiac.core :as z]))

;; A complete web app in 5 lines
(defn routes []
  [["/" {:get (fn [_] [:h1 "Hello World!"])}]])

(z/start! {:routes #'routes}) ; Start server on port 3000
```

## Features

### Batteries Included (But Replaceable)
- 🚪 **Smart Routing**: Reitit-based with path params, coercion, and middleware
- 📦 **Essential Middleware**: Sessions, cookies, CSRF, params parsing
- 💬 **Templating**: Hiccup HTML responses by default (overrideable)
- 🔌 **WebSockets**: Built-in async support
- ⚡ **Hot Reloading**: Develop without restarting
- 🛡 **Security**: Secure session cookies, CSRF protection
- 📡 **JSON API Ready**: Helpers for JSON requests/responses

### Philosophy
- **No hidden magic** - Everything explicit, nothing automatic
- **Your app structure** - No enforced directory layout
- **Your choices** - Bring your own database, auth, CSS framework
- **Escape hatches** - All components replaceable
- **Transparent stack** - Built on proven libraries (Ring, Reitit, Jetty)

## Why Zodiac is Different

Most Clojure web frameworks live on this spectrum:

```
[DIY Libraries] <-----------[Zodiac]-----------> [Full-stack Frameworks]
                      (Curated essentials)          (Rails-like opinions)
```

Where others might prescribe:
- Specific database libraries
- React/CLJS frontend setups
- Authentication systems
- Directory structures
- Deployment configurations

**Zodiac says no.** We provide the web toolkit - you choose the rest. Perfect when:
- You want Ring/Reitit best practices without the setup
- You're building a JSON API that might need HTML routes later
- You want to prototype fast but need room to grow
- You prefer choosing your own components

## Getting Started

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
