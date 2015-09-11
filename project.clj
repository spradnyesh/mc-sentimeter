(defproject mc-sentimeter "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.122"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [cljs-ajax "0.3.14"]
                 [cljs-http "0.1.37"]
                 ;; [enfocus "2.1.1"]
                 ;; [prismatic/dommy "1.1.0"]
                 [hickory "0.5.4"]
                 [reagent "0.5.0"]]

  :plugins [[lein-cljsbuild "1.1.0"]
            [lein-figwheel "0.3.9"]]

  :source-paths ["src"]

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]

  :cljsbuild {:builds [{:id "dev"
                        :source-paths ["src"]
                        :figwheel { :on-jsload "mc-sentimeter.core/on-js-reload" }
                        :compiler {:main mc-sentimeter.core
                                   :asset-path "js/compiled/out"
                                   :output-to "resources/public/js/compiled/mc_sentimeter.js"
                                   :output-dir "resources/public/js/compiled/out"
                                   :source-map-timestamp true }}
                       {:id "min"
                        :source-paths ["src"]
                        :compiler {:output-to "resources/public/js/compiled/mc_sentimeter.js"
                                   :main mc-sentimeter.core
                                   :optimizations :whitespace ; :advanced
                                   :pretty-print false}}]}

  :figwheel {:css-dirs ["resources/public/css"]
             :nrepl-port 7888})
