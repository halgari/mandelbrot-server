(defproject mandelbrot-server "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [com.taoensso/timbre "2.6.1"]
		 [core.async "0.1.0-SNAPSHOT"]
		 [com.nativelibs4java/javacl "1.0.0-RC3"]
                 [http-kit "2.1.10"]
                 [compojure "1.1.5"]
                 [org.clojure/clojurescript "0.0-1889"]]
  :plugins [[lein-cljsbuild "0.3.2"]]
  :main mandelbrot-server.core
  :cljsbuild
  {:builds
   [{:id "simple"
     :source-paths ["src-cljs"]
     :compiler {:optimizations :simple
                :pretty-print true
                :static-fns true
		#_:output-dir #_"resources/public/js"
                :output-to "resources/public/js/main.js"
		#_:source-map #_"main.js.map"}}
    #_{:id "adv"
     :source-paths ["src-cljs"]
     :compiler {:optimizations :advanced
                :pretty-print true
                :output-to "resources/public/main.js"}}]})
