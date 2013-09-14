(ns mandelbrot-server.image-processor
  (:require [mandelbrot-server.service :refer [ILifecycle when-loop start stop]]
            [clojure.core.async :refer :all]
            [clojure.java.io :as jio])
  (:import [java.awt Color Image Dimension]
           [java.awt.image BufferedImage]
           [org.bridj Pointer]
           [java.io ByteArrayOutputStream]
           [javax.imageio ImageIO]))

(set! *warn-on-reflection* true)


(defn convert-image ^BufferedImage [d width height] #_[d ^long width ^long height]
  (let [^Pointer ptr d
        img (BufferedImage. width height BufferedImage/TYPE_INT_ARGB)]
    (dotimes [x (long width)]
      (dotimes [y (long height)]
        (let [idx (+ (* y width) x)
              val (.getFloatAtIndex ptr idx)
              c (if (= val 1.0)
                  (Color/getHSBColor val 1 0)
                  (Color/getHSBColor (+ 0.5 val) 1.0 0.5))]
          (.setRGB img x y (.getRGB c)))))
    img))

(defn compress-image [^BufferedImage img]
  (let [out (ByteArrayOutputStream.)]
    (println [(.getWidth img) (.getHeight img)])
    (assert (ImageIO/write img "png" out))
    (.flush out)
    (.toByteArray out)))

(defn process-loop [{:keys [request-channel]}]
  (thread
   (when-loop [[[img width height] response-channel :as msg] (<!! request-channel)]
              (println msg)
              (>!! response-channel (-> img
                                        (convert-image (long width) (long height))
                                        compress-image)))))


(defrecord ImageProcessor [running?
                           request-channel
                           running-threads
                           number-of-workers]
  ILifecycle
  (start [service]
    (if running?
      service
      (assoc service
        :running? true?
        :running-threads (->> (for [x (range number-of-workers)]
                                (process-loop service))
                              (into [])))))
  (stop [service]
    (if (not running?)
      service
      (do (close! request-channel)
          (doseq [th running-threads]
            (<!! th))
          (assoc service
            :running? false
            :running-threads [])))))


(defn new-image-processor []
  (->ImageProcessor false (chan 4) [] 4))
