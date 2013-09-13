(ns main)


(.log js/console "foo")


(def web-socket (js/WebSocket. "ws://localhost:8080/ws"))

(set! (.-onopen web-socket)
      (fn [] (.log js/console "foobar")))
