# Routing

Zodiac uses [Reitit](https://github.com/metosin/reitit) for routing. Reitit is
an incredibly powerful routing library and is very well
[documented](https://cljdoc.org/d/metosin/reitit/). We won't duplicate Reitit's
documentation here but instead we'll only cover the basics and how to use Retit
with Zodiac.

To use routes with Zodiac associate a Retit route definition to the `:routes` key in
the [options](Readme.md#options) map passed to [[zodiac.core/start]].

The most basic example looks like:

```clojure
(ns myapp
  (:require [zodiac.core :as z]))

(def routes
  ["/" {:handler (fn [_]
                   {:status 200
                    :body "ok"})}])

(z/start {:routes routes})
```

## Coercion

The Retit router created when you start Zodiac comes preconfigured with route
[coercion](https://cljdoc.org/d/metosin/reitit/doc/coercion/coercion-explained)
using [Malli](https://github.com/metosin/malli). This means that you can
validate and coerce the request query, body, form, header and path using using
Malli schema definitions.

For example to coerce the `:id` keyword in the route path you can do the following:

```clojure
(def routes
  ["/resource/:id" {:handler (fn [{:keys [path-params]
                                   :as request}]
                               {:status 200
                                :body (if (int? (:id path-params))
                                        "Its an int!"
                                        "NOT an int!")})
                    :parameters {:path {:id int?}}}])
```

By default Reitit includes error handlers to pretty print coersion errors. To
override or disable the default error handlers use the `:error-handlers` option
passed to `zodiac.core/start`.

```clojure
(ns myapp
  (:require [reitit.coercion :as coercion]
            [zodiac.core :as z]))

(defn coersion-error-handler [req err]
  {:status 500
   :body "ERROR!"})

(z/start {:routes routes
          :error-handlers {::coercion/request-coercion coercion-error-handler}})
```

I encourage you to read more about defining schemas for different request parameters in the Reitit documentation for [definiting parameters]( https://cljdoc.org/d/metosin/reitit/0.7.2/doc/coercion/coercion-explained#defining-parameters)
