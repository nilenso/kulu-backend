(ns kulu-backend.web
  (:require [nrepl.server :as nrepl-server]
            [kulu-backend.handler :refer [app]]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.reload :refer [wrap-reload]]
            [clojure.tools.logging :as log]))

(defn -main [& [port nrepl-port]]
  (let [port (Integer. (or port (System/getenv "port") 3001))
        nrepl-port (Integer. (or nrepl-port 3002))]

    (log/info "Starting nREPL server on port" nrepl-port)
    (nrepl-server/start-server :port nrepl-port)

    (jetty/run-jetty
     (wrap-reload #'app)
     {:port port :join? false})))

