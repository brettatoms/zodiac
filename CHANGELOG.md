# Change Log

* 0.6.67 -- 2025-08-15
  * Add a :middleware option to make it easier to wrap the default middleware.

* 0.6.66 -- 2025-07-05
  * Use the logger instead of println

* 0.6.65 -- 2025-06-14
  * Switch from info.sunng/ring-jetty9-adapter to ring/ring-jetty-adapter

* 0.5.64 -- 2025-05-28
  * Bump info.sunng/ring-jetty9-adapter 0.37.3 ->  0.37.4
  * Bump metosin/malli 0.17.0 -> 0.18.0
  * Bump metosin/reitit 0.7.2 -> 0.9.1
  * Bump metosin/reitit-dev 0.7.2 -> 0.9.1
  * Bump metosin/reitit-middleware 0.7.2 -> 0.9.1

* 0.4.62 -- 2025-02-17
  * Bump info.sunng/ring-jetty9-adapter 0.35.1 ->  0.37.3
  * Bump org.clojure/data.json 2.4.0 -> 2.5.1
  * Bump ring/ring-core 1.13.0 -> 1.14.1
  * Bump ring/ring-devel 1.13.0 -> 1.14.1

* 0.4.61 -- 2025-02-17
  * Rollback Zodiac start if there's an error when initializing the system

* 0.4.58 -- 2025-02-12
  * Simplify the anti-forgery whitelist matching

* 0.4.52 -- 2025-02-09
  * Add anti-forgery-whitelist config option
  * bump metosin/malli 0.16.4 -> 0.17.0
  * bump ring/ring-anti-forgery 1.3.1 -> 1.4.0

* 0.4.48 -- 2024-12-15
  * Fix reload-per-request? config option

* 0.4.45 -- 2024-11-30
  * Enable malli coercion by default
  * Fix custom error handlers

* 0.3.38 -- 2024-11-17
  * Change middleware order so context is available in *request*

* 0.3.34 -- 2024-11-10
  * Allow extending the router via default handlers

* 0.3.33 -- 2024-10-31
  * Fix build release version

* 0.3.32 -- 2024-10-31
  * Make the router a separate component so it can be extended or replaced

* 0.2.31 -- 2024-10-30
  * Use Java 17 for build version
  * Improve docs
  * Make render-html-middleware private
  * bump info.sunng/ring-jetty9-adapter 0.35.0 -> 0.35.1
  * bump integrant/integrant 0.12.0 -> 0.13.1
  * bump metosin/malli 0.16.3 -> 0.16.4
  * bump ring/ring-core 1.12.2 -> 1.13.0
  * bump ring/ring-devel 1.12.2 -> 1.13.0

* 0.2.25 -- 2024-10-30
  * remove `zodiac.core/options-from-env`

* 0.1.19 -- 2024-10-29
  * First publicly announced version
