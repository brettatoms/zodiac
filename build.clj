(ns build
  (:require [clojure.tools.build.api :as b]
            [deps-deploy.deps-deploy :as dd]))

(def lib 'com.github.brettatoms/zodiac)
(def version (format "0.0.1"))
(def snapshot (str version "-SNAPSHOT"))
(def class-dir "target/classes")
(def jar-file (format "target/%s-%s.jar" (name lib) version))

;; delay to defer side effects (artifact downloads)
(def basis (delay (b/create-basis {:project "deps.edn"})))

(defn clean [_]
  (b/delete {:path "target"}))

(defn- pom-template [version]
  [[:description "A simple and extensible web framework for Clojure"]
   [:url "https://github.com/brettatoms/zodiac"]
   [:licenses
    [:license
     [:name "MIT License"]
     [:url "https://mit-license.org/license.txt"]]]
   [:developers
    [:developer
     [:name "Brett Adams"]]]
   [:scm
    [:url "https://github.com/brettatoms/zodiac"]
    [:connection "scm:git:https://github.com/brettatoms/zodiac.git"]
    [:developerConnection "scm:git:ssh:git@github.com:brettatoms/zodiac.git"]
    [:tag (str "v" version)]]])

(defn- jar-opts [opts]
  (let [version (if (:snapshot opts) snapshot version)]
    (println "\nVersion:" version)
    (assoc opts
           :lib lib   :version version
           :jar-file  (format "target/%s-%s.jar" lib version)
           :basis     (b/create-basis {})
           :class-dir class-dir
           :target    "target"
           :src-dirs  ["src"]
           :pom-data  (pom-template version))))

(defn jar [opts]
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis @basis
                :src-dirs ["src"]})
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/jar (jar-opts opts)))

(defn deploy "Deploy the JAR to Clojars." [opts]
  (let [{:keys [jar-file] :as opts} (jar-opts opts)]
    (dd/deploy {:installer :remote :artifact (b/resolve-path jar-file)
                :pom-file (b/pom-path (select-keys opts [:lib :class-dir]))}))
  opts)