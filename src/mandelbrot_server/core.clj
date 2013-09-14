(ns mandelbrot-server.core
  (:require [mandelbrot-server.image-processor :as ip]
            [mandelbrot-server.opencl :as ocl]
            [mandelbrot-server.service :refer [ILifecycle start stop]]
            [mandelbrot-server.http-server :as http]
            [mandelbrot-server.globals :refer [system-state]]
            [clojure.core.async :refer :all]
            [clojure.java.io :as jio]))



(defrecord MainSystem [running?
                       image-processor
                       opencl-service
                       http-server]
  ILifecycle
  (start [service]
    (if running?
      service
      (assoc service
        :image-processor (start image-processor)
        :opencl-service (start opencl-service)
        :http-server (start http-server)
        :running? true)))
  (stop [service]
    (if (not running?)
      service
      (assoc service
        :running? false
        :image-processor (stop image-processor)
        :opencl-service (stop opencl-service)
        :http-server (stop http-server)))))


(defn reset-system []
  (reset! system-state (->MainSystem false
                                     (ip/new-image-processor)
                                     (ocl/mandelbrot-server)
                                     (http/new-http-server))))

(defn start-system []
  (when-not @system-state
    (reset-system))
  (swap! system-state start))

(defn stop-system []
  (swap! system-state stop)
  (reset! system-state nil))


(defn generate-image [& args]
  (let [c (chan 1)]
    (put! (-> @system-state
              :opencl-service
              :request-channel)
          [args c])
    c))

(defn compress-iamge [img]
  (let [c (chan 1)]
    (put! (-> @system-state
              :image-processor
              :request-channel)
          [[img 512 512] c])
    c))

(defn write-image [img]
  (println img (count img))
  (with-open [of (jio/output-stream "test.png")]
    (jio/copy img of)))

(defn try-execute []
  (let [img (<!! (generate-image (float 1000)))
        com (<!! (compress-iamge img))]
    (write-image com)))


(defn -main []
  (start-system))
