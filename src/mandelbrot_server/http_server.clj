(ns mandelbrot-server.http-server
  (:require [mandelbrot-server.service :refer [ILifecycle start stop]]
            [clojure.core.async :refer :all]
            [mandelbrot-server.globals :refer [system-state] :as globals]
            [clojure.edn :as edn]
            [org.httpkit.server :refer :all]
            [compojure.route :as route]
            [compojure.handler :refer [site]]
            [compojure.core :refer [routes GET POST]]
            [taoensso.timbre :as log]))

(def server (atom nil))

(def connected-clients (atom {}))

(defn remove-client [state k channel]
  (when-let [c (get-in state [k channel])]
    (close! c))
  (update-in state [k] dissoc channel))

(defn handle-image-requests [wc c]
  (go
   (loop []
     (when-let [request (<! c)]
       (log/info request)
       (let [{:keys [zoom offset-y offset-x] :as parsed-request} (edn/read-string request)
             tmpc (chan 1)
             img (<! (globals/opencl-request tmpc
                                             (float 1000)
                                             (float zoom)
                                             (float offset-x)
                                             (float offset-y)))
             compressed (<! (globals/compress-image tmpc img))]
         #_(globals/write-image compressed)
         (send! wc compressed))
       (recur)))))

(defn handle-stats-requests [wc c]
  (go
   (loop []
     (when-let [request (<! c)]
       (log/info request)
       (send! wc (pr-str globals/stats)))
     (recur))))

(defn make-channel [connected-clients k channel handler]
  (swap! connected-clients
         (fn [state]
           (if (state channel)
             state
             (let [c (chan (sliding-buffer 100))]
               (handler channel c)
               (assoc-in state [k channel] c))))))

(defn async-handler [{:keys [connected-clients] :as service} k handler req]
  (log/info "async")
  (with-channel req channel
    (log/info "channel")
    (on-close channel (fn [status]
                        (swap! connected-clients remove-client k channel)))
    (make-channel connected-clients k channel handler)
    (on-receive channel
                (fn [data]
                  (log/info "Connection:" channel)
                  (when-let [c (get-in @connected-clients [k channel])]
                    (put! c data))))))

(defn start-stats-updater [{:keys [connected-clients] :as service}]
  (go
   (while true
     (log/info @globals/stats)
     (let [val (<! globals/stats-update-chan)]
       (doseq [[wc] (:stats @connected-clients)]
         (send! wc (pr-str @globals/stats)))))))

(defrecord HTTPServer [connected-clients
                       running?
                       port
                       route-defs
                       server]
  ILifecycle
  (start [service]
    (if running?
      service
      (let [rs (routes
                (GET "/images" [] (partial async-handler service :images handle-image-requests))
                (GET "/stats" [] (partial async-handler service :stats handle-stats-requests))
                (route/resources ""))
            server (run-server rs {:port port})]
        (start-stats-updater service)
        (assoc service
          :running? true
          :route-defs rs
          :server server))))
  (stop [service]
    (if (not running?)
      service
      (do (doseq [[ws c] @connected-clients]
            (close ws)
            (close! c))
          (assoc service
            :connected-clients (atom {})
            :running? false
            :server nil
            :route-defs nil)))))


(defn new-http-server []
  (->HTTPServer (atom {}) false 8080 nil nil))
