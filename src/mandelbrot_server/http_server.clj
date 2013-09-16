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

(def connected-clients (atom []))

(defn remove-client [state channel]
  (when-let [c (get state channel)]
    (close! c))
  (dissoc state channel))

(defn handle-requests [wc c]
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

(defn make-channel [connected-clients channel]
  (swap! connected-clients
         (fn [state]
           (if (state channel)
             state
             (let [c (chan (sliding-buffer 100))]
               (handle-requests channel c)
               (assoc state channel c))))))

(defn async-handler [{:keys [connected-clients] :as service} req]
  (log/info "async")
  (with-channel req channel
    (log/info "channel")
    (on-close channel (fn [status]
                        (swap! connected-clients remove-client channel)))
    (make-channel connected-clients channel)
    (on-receive channel
                (fn [data]
                  (log/info "Connection:" channel)
                  (when-let [c (get @connected-clients channel)]
                            (put! c data))))))

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
                (GET "/ws" [] (partial async-handler service))
                (route/resources ""))
            server (run-server rs {:port port})]
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
