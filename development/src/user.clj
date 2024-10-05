(ns user
  (:require [integrant.core :as ig]
            [taoensso.telemere.tools-logging :as tt]
            [zodiac.core :as z]))

(add-tap println)
(tt/tools-logging->telemere!) ;; send tools.logging to telemere

(def ^:dynamic *system*)

(defn routes []
  [""
   ["/" {:handler (constantly {:status 200
                               :body "ok"})}]
   ["/exception" {:handler (fn [_]
                             (throw (Exception. "something terrible happened"))) }]
   ["/ex-info" {:handler (fn [_]
                             (throw (ex-info "something terrible happened" {}))) }]])


(defn go []
  (let [sys (z/start {:routes #'routes
                      :reload-per-request? true})]
    (alter-var-root #'*system* (constantly sys))))

(defn stop []
  (when *system*
    (ig/halt! *system*)
    (alter-var-root #'*system* (constantly nil))))
