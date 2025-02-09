# Change Log

* Unreleased -- 2025-02-09
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
