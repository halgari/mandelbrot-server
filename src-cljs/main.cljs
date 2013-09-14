(ns main
  (:require [cljs.core.async :refer [<! >! chan put! dropping-buffer]])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(.log js/console "foo")


(def web-socket (js/WebSocket. "ws://localhost:8080/ws"))
(set! (.-binaryType web-socket) "arraybuffer")

(set! (.-onopen web-socket)
      (fn [] (.log js/console "foobar")
        (put! web-socket-send-chan 42)))


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

(defn image-from-data [data]
  (time
   (let [img (js/Image.)]
     (set! (.-src img)
           (str "data:image/jpeg;base64," (arraybuffer-to-base64 data)))
     img)))

(set! (.-onmessage web-socket)
      (fn [event]
        (.log js/console event)
        (.replaceChild (.-parentNode (image-container))
                       (image-from-data (.-data event))
                       (image-container))))

(go (try (loop []
           (let [val (<! web-socket-send-chan)]
             (.log js/console "sending")
             (.send web-socket (pr-str val))
             (recur)))
         (catch js/Object ex (.log js/console ex))))
