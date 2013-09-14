(ns mandelbrot-server.globals
  (:require [clojure.java.io :as jio]
            [clojure.core.async :refer :all]))

(def system-state (atom nil))

(defn opencl-request [response-chan & args]
  (let [c (-> @system-state :opencl-service :request-channel)]
    (put! c [args response-chan])
    response-chan))

(defn compress-image [response-chan img]
  (let [c (-> @system-state :image-processor :request-channel)]
    (put! c [[img 512 512] response-chan])
    response-chan))

(defn write-image [img]
  (println img (count img))
  (with-open [of (jio/output-stream "test.png")]
    (jio/copy img of)))
