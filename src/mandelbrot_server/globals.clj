(ns mandelbrot-server.globals
  (:require [clojure.java.io :as jio]
            [clojure.core.async :refer :all]))

(def system-state (atom nil))

(def stats-update-chan (chan (sliding-buffer 1)))
(def stats (atom {:render-count 0}))
(add-watch stats :update-chan
           (fn [k r o n]
             (put! stats-update-chan :tick)))


(defn opencl-request [response-chan & args]
  (let [c (-> @system-state :opencl-service :request-channel)]
    (put! c [args response-chan])
    (swap! stats update-in [:render-count] inc)
    response-chan))

(defn compress-image [response-chan img]
  (let [c (-> @system-state :image-processor :request-channel)]
    (put! c [[img 890 512] response-chan])
    response-chan))

(defn write-image [img]
  (println img (count img))
  (with-open [of (jio/output-stream "test.png")]
    (jio/copy img of)))
