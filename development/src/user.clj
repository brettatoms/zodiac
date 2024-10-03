(ns user
  (:require [zodiac.core :as z]
            [integrant.core :as ig]))

(add-tap println)

(def ^:dynamic *system*)

(defn routes []
  ["/" {:handler (constantly {:status 200
                              :body "ok"})}])


(defn go []
  (let [sys (z/start {:routes #'routes
                      :reload-per-request? true})]
    (alter-var-root #'*system* (constantly sys))))

(defn stop []
  (when *system*
    (ig/halt! *system*)
    (alter-var-root #'*system* (constantly nil))))
