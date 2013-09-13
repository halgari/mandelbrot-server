(ns mandelbrot-server.service
  (:require [clojure.stacktrace :as st]))

(defprotocol ILifecycle
  (start [this])
  (stop [this]))


(defmacro when-loop [[nm expr] & body]
  `(loop []
     (when-let [~nm ~expr]
       (try ~@body
            (catch Throwable ex# (do (println "Error! " ex#)
                                     (st/print-stack-trace ex#))))
       (recur))))
