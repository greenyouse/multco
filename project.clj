(defproject com.greenyouse/multco "0.1.2-beta" ;multi cogo (caching)
  :description "Clientside cljs databases across platforms"
  :url "https://github.com/greenyouse/multco"
  :license {:name "BSD 2-Clause"
            :url "http://www.opensource.org/licenses/BSD-2-Clause"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-3126"]
                 [org.clojure/core.logic "0.8.10"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [fogus/bacwn "0.4.0"]
                 [datascript "0.10.0"]
                 [com.greenyouse/clodexeddb "0.1.0"]]

  :profiles {:dev {:dependencies [[weasel "0.6.0"]
                                  [com.cemerick/piggieback "0.1.6-SNAPSHOT"]]
                   :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}}}

  :plugins [[lein-cljsbuild "1.0.5"]]

  :cljsbuild {:builds [{:id "dev"
                        :source-paths ["src" "dev"]
                        :compiler {:main multco.core
                                   :output-to "multco.js"
                                   :output-dir "out"
                                   :optimizations :none
                                   :source-map true
                                   :warnings {:single-segment-namespace false}}}]})
