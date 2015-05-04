(defproject cdb "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :plugins [
    [lein-cljsbuild "1.0.4"]
  ]

  :clean-targets ["web"]

  :dependencies [[org.clojure/clojure "1.7.0-alpha5"]
                 [cljs-ajax "0.3.9"]
                 [org.clojure/clojurescript "0.0-2760"]
                 [prismatic/dommy "1.0.0"]
                 [datascript "0.8.2-SNAPSHOT"]
                 [rum "0.2.2"]
                 ]

  :cljsbuild {
    :builds [
      { :id "prod"
        :source-paths  ["src"]
        :notify-command ["twmnc" "-t cljsbuild" "-c anketa.prod"]
        :compiler {
          :output-to     "web/app.min.js"
          :optimizations :advanced
          :pretty-print  false
          :elide-asserts true
          :cache-analysis true
          :language-out :ecmascript5
          :language-in :ecmascript5
        }}
      { :id "dev"
        :source-paths  ["src"]
        :notify-command ["twmnc" "-t cljsbuild" "-c anketa.dev"]
        :compiler {
          :output-to     "web/app.js"
          :output-dir    "web/target-cljs"
          :optimizations :none
          :source-map    "web/app.js.map"
        }}
  ]})
