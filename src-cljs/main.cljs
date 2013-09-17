(ns main
  (:require [cljs.core.async :refer [<! >! chan put! dropping-buffer]])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(def image-state (atom {:zoom 1.0
                        :offset-x 0
                        :offset-y 0}))

(.log js/console "foo")


(def image-web-socket (js/WebSocket. "ws://localhost:8080/images"))
(set! (.-binaryType image-web-socket) "arraybuffer")
(set! (.-onopen image-web-socket)
      (fn [] (.log js/console "foobar")
        (put! web-socket-send-chan @image-state)))

(def stats-web-socket (js/WebSocket. "ws://localhost:8080/stats"))
(set! (.-onmessage stats-web-socket)
      (fn [event]
        (.log js/console (str "stats" (.-data event)))
        (set! (.-value (.getElementById js/document "stats"))
              (.-data event))))


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

(defn link-button [button k op & args]
  (println button)
  (.addEventListener (.getElementById js/document button)
                     "click"
                     #(do
                        (apply swap! image-state update-in [k] op args)
                          (put! web-socket-send-chan @image-state))))

(link-button "reload" :zoom identity)
(link-button "zoom-in" :zoom + 0.1)
(link-button "y-plus" :offset-y + 10)
(link-button "y-minus" :offset-y - 10)
(link-button "x-plus" :offset-x + 10)
(link-button "x-minus" :offset-x - 10)

(defn image-from-data [data]
  (time
   (let [img (js/Image.)]
     (set! (.-src img)
           (str "data:image/jpeg;base64," (arraybuffer-to-base64 data)))
     (set! (.-id img) "display-image")
     img)))

(set! (.-onmessage image-web-socket)
      (fn [event]
        (.log js/console event)
        (.replaceChild (.-parentNode (image-container))
                       (image-from-data (.-data event))
                       (image-container))))

(go (try (loop []
           (let [val (<! web-socket-send-chan)]
             (.log js/console (pr-str "sending" val))
             (.send image-web-socket (pr-str val))
             (recur)))
         (catch js/Object ex (.log js/console ex))))
