(ns main
  (:require [cljs.core.async :refer [<! >! chan put! dropping-buffer]])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(def image-state (atom {:zoom 1.0
                        :offset-x 0
                        :offset-y 0}))

(.log js/console "foo")


(def web-socket (js/WebSocket. "ws://localhost:8080/ws"))
(set! (.-binaryType web-socket) "arraybuffer")

(set! (.-onopen web-socket)
      (fn [] (.log js/console "foobar")
        (put! web-socket-send-chan @image-state)))


(def web-socket-recv-chan (chan (dropping-buffer 10)))
(def web-socket-send-chan (chan 1))

(defn image-container []
  (.getElementById js/document "display-image"))

(defn arraybuffer-to-base64 [buffer]
  (let [bytes (js/Uint8Array. buffer)]
    (loop [binary ""
           i 0]
      (if (< i (.-byteLength bytes))
        (recur (str binary (.fromCharCode js/String (aget bytes i)))
               (inc i))
        (.btoa js/window binary)))))

(set! *print-fn* (fn [x]
                   (.log js/console x)))

(.addEventListener (.getElementById js/document "reload")
                   "click"
                   #(put! web-socket-send-chan @image-state))

(.addEventListener (.getElementById js/document "zoom-in")
                   "click"
                   #(do (swap! image-state update-in [:zoom] + 0.1)
                        (put! web-socket-send-chan @image-state)))

(.addEventListener (.getElementById js/document "y-plus")
                   "click"
                   #(do (swap! image-state update-in [:offset-y] + 10)
                        (put! web-socket-send-chan @image-state)))

(.addEventListener (.getElementById js/document "y-minus")
                   "click"
                   #(do (swap! image-state update-in [:offset-y] - 10)
                        (put! web-socket-send-chan @image-state)))

(.addEventListener (.getElementById js/document "x-plus")
                   "click"
                   #(do (swap! image-state update-in [:offset-x] + 10)
                        (put! web-socket-send-chan @image-state)))

(.addEventListener (.getElementById js/document "x-minus")
                   "click"
                   #(do (swap! image-state update-in [:offset-x] - 10)
                        (put! web-socket-send-chan @image-state)))



(defn image-from-data [data]
  (time
   (let [img (js/Image.)]
     (set! (.-src img)
           (str "data:image/jpeg;base64," (arraybuffer-to-base64 data)))
     (set! (.-id img) "display-image")
     img)))

(set! (.-onmessage web-socket)
      (fn [event]
        (.log js/console event)
        (.replaceChild (.-parentNode (image-container))
                       (image-from-data (.-data event))
                       (image-container))))

(go (try (loop []
           (let [val (<! web-socket-send-chan)]
             (.log js/console (pr-str "sending" val))
             (.send web-socket (pr-str val))
             (recur)))
         (catch js/Object ex (.log js/console ex))))
