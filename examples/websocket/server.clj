(ns server
   (:require [ring.websocket :as ws]
           [taoensso.telemere.tools-logging :as tt]
           [dev.onionpancakes.chassis.core :as c]
           [zodiac.core :as z])
 (:gen-class))

(tt/tools-logging->telemere!) ;; send tools.logging to telemere

(declare stop)

(def ^:dynamic *system*)

(defn shutdown [socket]
  (ws/send socket "Shutting down :(")
  (ws/close socket)
  (when *system*
    (z/stop *system*)
    (alter-var-root #'*system* (constantly nil))))

(defn handler [_request]
  [:div
   [:h1 "Websocket example"]
   [:div "Send any message or \"exit\" to shutdown the server."
    [:input#input {:onchange "sendMessage()"}]
    [:button {:onclick "sendMessage()"} "Send"]
    [:div#result ""]
    [:script (c/raw """
  function sendMessage() {
    var value = document.getElementById('input').value;
    ws.send(value)
  };

  var ws = new WebSocket('http://localhost:3000/ws');
  ws.onopen = (event) => {
    console.log('Connection opened')
  };
  ws.onmessage = (event) => {
    document.getElementById('result').textContent = event.data
  };
""")]]])

(defn ws-handler
  "The websocket handler."
  [request]
  (assert (ws/upgrade-request? request))
  {::ws/listener {:on-open (fn [socket]
                             (ws/send socket "Connection open!"))
                  :on-message (fn [socket message]
                                (if (= message "exit")
                                  (shutdown socket)
                                  (ws/send socket (format "Got \"%s\"!" message))))}})

(defn routes []
  [""
   ["/" {:handler handler}]
   ["/ws" {:handler ws-handler}]])

(defn -main [& _]
  (let [sys (z/start {:routes #'routes
                      :reload-per-request? true})]
    ;; Assign the system to a dynamic var so we can stop it later
    (alter-var-root #'*system* (constantly sys))
    (println "Ready to go...")))
