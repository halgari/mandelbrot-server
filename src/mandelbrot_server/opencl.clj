(ns mandelbrot-server.opencl
  (:require [mandelbrot-server.service :refer [ILifecycle when-loop start stop]]
            [clojure.core.async :refer :all]
            [clojure.java.io :as io])
  (:import [com.nativelibs4java.opencl CLContext CLQueue JavaCL CLMem$Usage CLProgram CLBuffer
            CLKernel CLDevice$QueueProperties CLEvent]
           [org.bridj Pointer]
           [java.nio ByteOrder]))

#_(set! *warn-on-reflection* true)

(def IMAGE-SIZE 512)
(def ^Long BUFFER-SIZE (* IMAGE-SIZE IMAGE-SIZE))


(defn service-loop [{:keys [request-channel ^CLKernel kernel ^CLQueue queue ^CLBuffer out-buffer] :as service}]
  (assert kernel)
  (assert queue)
  (assert out-buffer)
  (thread
   (println "looping")
   (when-loop [[args response-channel :as msg] (<!! request-channel)]
              (println msg out-buffer)
              (.setArgs kernel (into-array Object (concat [out-buffer IMAGE-SIZE IMAGE-SIZE] args)))
              (let [evt (.enqueueNDRange kernel queue (int-array [IMAGE-SIZE IMAGE-SIZE])
                                         (into-array CLEvent []))
                    ptr (.read out-buffer queue (into-array CLEvent [evt]))]
                (println "done")
                (>!! response-channel
                     ptr)))))


(defn start-threads [{:keys [number-of-workers] :as service}]
  (reduce
   (fn [acc idx]
     (assoc-in acc [:running-threads idx] (service-loop service)))
   service
   (range number-of-workers)))

(defrecord OpenCLService [^CLContext context
                          ^CLQueue queue
                          ^ByteOrder byte-order
                          ^String program-src
                          ^String entry-point
                          ^CLBuffer out-buffer
                          ^CLKernel kernel
                          number-of-workers
                          request-channel
                          running?
                          running-threads]
  ILifecycle
  (start [service]
    (if running?
      service
      (let [context (JavaCL/createBestContext)
            queue (.createDefaultProfilingQueue context)
            byte-order (.getByteOrder context)
            out-buffer (.createFloatBuffer context CLMem$Usage/Output BUFFER-SIZE)
            program (.createProgram context (into-array String [program-src]))
            kernel (.createKernel program entry-point (into-array Object []))]
        (-> service
            (assoc :context context
                   :queue queue
                   :kernel kernel
                   :byte-order byte-order
                   :out-buffer out-buffer)
            (start-threads)))))
  (stop [service]
    (if (not running?)
      service
      (do (close! request-channel)
          (doseq [th running-threads]
                  (<!! th))
          (assoc service
            :running false)))))

(defn new-opencl [source-code kernel-name]
  (map->OpenCLService
   {:running? false
    :number-of-workers 1
    :request-channel (chan 1)
    :running-threads []
    :program-src source-code
    :entry-point kernel-name}))


(defn mandelbrot-server []
  (new-opencl (slurp (io/resource "mandelbrot.cl"))
              "mandelbrot"))
