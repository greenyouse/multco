(defproject com.greenyouse/pldb-cache "0.1.0"
  :description "Clientside caching for pldb"
  :url "https://github.com/greenyouse/pldb-cache"
  :license {:name "BSD 2-Clause"
            :url "http://www.opensource.org/licenses/BSD-2-Clause"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2816"]
                 [org.clojure/core.logic "0.8.8"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 ;;[weasel "0.6.0-SNAPSHOT"]
                 [com.greenyouse/clodexeddb "0.1.0"]]

  :plugins [[lein-cljsbuild "1.0.4"]]

  :cljsbuild {:builds [{:id "dev"
                        :source-paths ["src" "dev"]
                        :compiler {:main pldb-cache.core
                                   :output-to "pldb_cache.js"
                                   :output-dir "out"
                                   :optimizations :none
                                   :source-map true}}]})
