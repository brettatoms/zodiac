# Change Log

* 1.0.xxx -- Unreleased
  * Add the top-level `:async?` option to run the server in async mode. This enables Jetty async mode and installs an adapter so synchronous and asynchronous handlers can coexist.
  * Add the `:zodiac/async?` route data to opt a route's handler into the async (3-arity `[request respond raise]`) contract. Handlers are synchronous by default, so ordinary handlers keep working in async mode. This supersedes the experimental async support from 0.9.98, which required every handler to be written in 3-arity form.
  * Support the `:zodiac.core/async?` and `:zodiac.core/skip-csrf` keyword forms (i.e. `::z/async?` and `::z/skip-csrf` when `zodiac.core` is aliased to `z`) as aliases for `:zodiac/async?` and `:zodiac/skip-csrf`.
  * Add the `examples/async-sse` example demonstrating a Server-Sent Events stream alongside a synchronous handler.
  * Add documentation for async requests (`doc/async.md`), including how streaming interacts with request threads and a virtual-thread thread-pool recipe.
  * Add a weekly OWASP/NVD dependency vulnerability scan (`.github/workflows/security-scan.yml`) and the `:nvd` alias.
  * Harden GitHub Actions workflows against supply-chain attacks by pinning actions to commit SHAs, and add Dependabot for GitHub Actions.
  * Pin patched Jackson (2.22.1) to clear CVE-2026-54512/54513/54515. Jackson is only pulled in transitively; Zodiac uses charred for JSON.
  * Bump org.clojure/clojure 1.12.4 -> 1.12.5
  * Bump com.cnuernber/charred 1.039 -> 1.041
  * Bump nubank/matcher-combinators 3.10.0 -> 3.10.1
  * Bump dev.weavejester/cljfmt 0.16.4 -> 0.16.5

* 0.9.100 -- 2026-06-27
  * Bump com.cnuernber/charred 1.038 -> 1.039
  * Bump org.clojure/tools.logging 1.3.0 -> 1.3.1
  * Bump ring/ring-core 1.15.3 -> 1.15.5
  * Bump ring/ring-devel 1.15.3 -> 1.15.5
  * Bump ring/ring-jetty-adapter 1.15.3 -> 1.15.5
  * Bump com.taoensso/telemere 1.2.0 -> 1.2.1
  * Bump com.taoensso/telemere-slf4j 1.2.0 -> 1.2.1
  * Bump nubank/matcher-combinators 3.9.2 -> 3.10.0
  * Bump org.clojure/test.check 1.1.2 -> 1.1.3
  * Bump io.github.clojure/tools.build 0.10.11 -> 0.10.14
  * Bump slipset/deps-deploy 0.2.2 -> 0.2.5
  * Bump clj-kondo/clj-kondo 2025.10.23 -> 2026.05.25
  * Bump dev.weavejester/cljfmt 0.15.6 -> 0.16.4

* 0.9.98 -- 2026-03-30
  * Add `:cookie-name` option
  * Add `:zodiac/skip-csrf` route data to disable CSRF protection per-route (uses compiled middleware for better performance)
  * Deprecate `:anti-forgery-whitelist` option in favor of `:zodiac/skip-csrf` route data
  * Experimental support for async handlers (3-arity `[request respond raise]`). Note: dynamic vars (`*request*`, `*router*`, `*session*`) are not available in async mode.
  * Bump metosin/malli 0.20.0 -> 0.20.1
  * Bump metosin/reitit 0.9.2 -> 0.10.1
  * Bump metosin/reitit-dev 0.9.2 -> 0.10.1
  * Bump metosin/reitit-middleware 0.9.2 -> 0.10.1
  * Bump com.cnuernber/charred 1.037 -> 1.038

* 0.8.89 -- 2025-12-11
  * Improved startup error handling: invalid options now throw instead of returning nil
  * Improved startup failure rollback to catch all exception types and properly halt partially-started components in reverse order
  * Added comprehensive test coverage for middleware and request/response flow (40+ new tests)
  * Added documentation for error handling, sessions & flash, request/response, middleware, and extensions
  * Replace clojure.data.json with charred for faster json decoding/encoding
  * Bump org.clojure/clojure 1.12.0 -> 1.12.4
  * Bump integrant/integrant 0.13.1 -> 1.0.1
  * Bump metosin/malli 0.18.0 -> 0.20.0
  * Bump metosin/reitit 0.9.1 -> 0.9.2
  * Bump metosin/reitit-dev 0.9.1 -> 0.9.2
  * Bump metosin/reitit-middleware 0.9.1 -> 0.9.2
  * Bump ring/ring-core 1.14.1 -> 1.15.3
  * Bump ring/ring-devel 1.14.1 -> 1.15.3
  * Bump ring/ring-jetty-adapter 1.14.1 -> 1.15.3

* 0.7.78 -- 2025-08-18
  * Tighten up options validation.
  * Make :middleware option wrap the entire app instead of only route handler middleware
    which we can already do when defining routes.
  * Allow injecting default handlers before the builtin default handlers

* 0.6.69 -- 2025-08-15
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
