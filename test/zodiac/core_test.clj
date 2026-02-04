(ns zodiac.core-test
  (:require [charred.api :as charred]
            [clojure.test :refer :all]
            [integrant.core :as ig]
            [malli.core :as m]
            [matcher-combinators.test :refer [match?]]
            [peridot.core :as peri]
            [reitit.ring :as rr]
            [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [spy.core :as spy]
            [zodiac.core :as z]))

(defn test-client [options]
  (-> {:start-server? false
       :cookie-secret "1234567890123456"}
      (merge options)
      z/start
      ::z/app))

(defn csrf-handler [_]
  {:body *anti-forgery-token*})

;;; ==========================================================================
;;; Response Helper Tests
;;; ==========================================================================

(deftest url-for
  (testing "should accept only a route name"
    (binding [z/*router* (rr/router ["/" {:name :root}])]
      (is (match? "/" (z/url-for :root)))))
  (testing "should accept only a path name"
    (binding [z/*router* (rr/router ["/" {:name :root}])]
      (is (match? "/" (z/url-for "/")))))
  (testing "should accept route args"
    (binding [z/*router* (rr/router ["/:id" {:name :root}])]
      (is (match? "/1" (z/url-for :root {:id 1})))))
  (testing "should accept route args"
    (binding [z/*router* (rr/router ["/:id" {:name :root}])]
      (is (match? "/1" (z/url-for :root {:id 1})))))
  (testing "should accept route args and query params"
    (binding [z/*router* (rr/router ["/:id" {:name :root}])]
      (is (match? "/1?q=something"
                  (z/url-for :root {:id 1} {"q" "something"})))))
  (testing "should return nil for invalid path args"
    (binding [z/*router* (rr/router ["/:id" {:name :root}])]
      (is (nil? (z/url-for :root {:id2 1}))))))

(deftest html-response
  (testing "should render html"
    (is (match? {:status 200
                 :headers {"content-type" "text/html"}
                 :body "<!DOCTYPE html><hi></hi>"}
                (z/html-response [:hi]))))
  (testing "should render html with status "
    (is (match? {:status 123
                 :headers {"content-type" "text/html"}
                 :body "<!DOCTYPE html><hi></hi>"}
                (z/html-response 123 [:hi]))))
  (testing "shouldn't render string body"
    (is (match? {:status 200
                 :headers {"content-type" "text/html"}
                 :body "hi"}
                (z/html-response "hi")))))

(deftest json-response
  (testing "should render josn"
    (is (match? {:status 200
                 :headers {"content-type" "application/json"}
                 :body "{\"x\":1}"}
                (z/json-response {:x 1}))))
  (testing "should render json with status "
    (is (match? {:status 123
                 :headers {"content-type" "application/json"}
                 :body "{\"x\":1}"}
                (z/json-response 123 {:x 1}))))
  (testing "shouldn't render string body"
    (is (match? {:status 200
                 :headers {"content-type" "application/json"}
                 :body "hi"}
                (z/json-response "hi")))))

;;; ==========================================================================
;;; Context & Extensions Tests
;;; ==========================================================================

(defmethod ig/init-key ::service [_ {:keys [value]}]
  value)

(deftest wrap-context
  (testing "puts data in request context"
    (let [handler (spy/spy (fn [_] {}))
          app (test-client {:request-context {:db "something"}
                            :routes ["/" {:name :root
                                          :handler handler}]})]
      (app {:request-method :get
            :uri "/"})
      (is (match? {::z/context {:db "something"}}
                  (first (spy/first-call handler)))))))

(deftest extensions
  (testing "extensions can modify the system config"
    (let [handler (spy/spy (fn [_] {}))
          app (test-client {:routes ["/" {:name :root
                                          :handler handler}]
                            :extensions [(fn [cfg]
                                           (-> cfg
                                               (assoc ::service {:value "something"})
                                               (assoc-in [::z/middleware :context :service] (ig/ref ::service))))]})]
      (app {:request-method :get
            :uri "/"})
      (is (match? {::z/context {:service "something"}}
                  (first (spy/first-call handler)))))))

;;; ==========================================================================
;;; Request/Response Parsing Tests
;;; ==========================================================================

(deftest render-html-middlware
  (testing "returning vector renders html"
    (let [app (test-client {:routes ["/" (constantly [:hi])]})]
      (is (match? {:status 200
                   :headers {"content-type" "text/html"}
                   :body "<!DOCTYPE html><hi></hi>"}
                  (app {:request-method :get
                        :uri "/"}))))))

(deftest parses-form-params
  (testing "parses form params"
    (let [post-handler (fn [{:keys [form-params]}]
                         (z/html-response (get form-params "value")))
          app (test-client {:routes ["/" {:get csrf-handler
                                          :post post-handler}]})
          ;; Get the CSRF token
          sess (-> (peri/session app)
                   (peri/request "/"))
          token (-> sess :response :body)
          resp (-> sess
                   (peri/request "/"
                                 :request-method :post
                                 :headers {"X-CSRF-Token" token}
                                 :params {:value "hi"})
                   :response)]
      (is (match? {:status 200
                   :headers {"content-type" "text/html"}
                   :body "hi"}
                  resp)))))

(deftest parses-json-body
  (testing "parses json body params"
    (let [post-handler (fn [{:keys [body-params] :as request}]
                         (z/html-response (:hello body-params)))
          app (test-client {:routes ["/" {:get csrf-handler
                                          :post post-handler}]})
          ;; Get the CSRF token
          sess (-> (peri/session app)
                   (peri/request "/"))
          token (-> sess :response :body)
          resp (-> sess
                   (peri/request "/"
                                 :request-method :post
                                 :headers {"X-CSRF-Token" token}
                                 :content-type "application/json"
                                 :body "{\"hello\": \"world\"}")
                   :response)]
      (is (match? {:status 200
                   :headers {"content-type" "text/html"}
                   :body "world"}
                  resp))))

  (testing "handles invalid json"
    (let [post-handler (constantly nil)  ;; never called
          app (test-client {:routes ["/" {:get csrf-handler
                                          :post post-handler}]})
          ;; Get the CSRF token
          sess (-> (peri/session app)
                   (peri/request "/"))
          token (-> sess :response :body)
          resp (-> sess
                   (peri/request "/"
                                 :request-method :post
                                 :headers {"X-CSRF-Token" token}
                                 :content-type "application/json"
                                 :body "not json")
                   :response)]
      (is (match? {:status 400
                   :body "Malformed \"application/json\" request."}
                  resp)))))

;;; ==========================================================================
;;; Exception Handling Tests
;;; ==========================================================================

(deftest exception-handling
  (testing "handles exceptions"
    (let [handler (fn [_] (throw (ex-info "something" {})))
          app (test-client {:routes ["/" {:get handler}]})
          resp (app {:request-method :get
                     :uri "/"})]
      (is (match? {:status 500
                   :body "Unknown error"}
                  resp))))

  (testing "custom error handler"
    (let [handler (fn [_] (throw (ex-info "something" {})))
          exc-handler (fn [_exception _request]
                        {:status 500
                         :body "this is the custom handler"})
          app (test-client {:routes ["/" {:get handler}]
                            :error-handlers {Exception exc-handler}})
          resp (app {:request-method :get
                     :uri "/"})]
      (is (match? {:status 500
                   :body "this is the custom handler"}
                  resp))))

  (testing "custom error handler - ex-info type"
    (let [handler (fn [_] (throw (ex-info "something" {:type ::exception})))
          exc-handler (fn [_exception _request]
                        {:status 500
                         :body "this is the custom handler"})
          app (test-client {:routes ["/" {:get handler}]
                            :error-handlers {::exception exc-handler}})
          resp (app {:request-method :get
                     :uri "/"})]
      (is (match? {:status 500
                   :body "this is the custom handler"}
                  resp)))))

(deftest exception-handling-receives-exception-data
  (testing "custom error handler receives exception and request"
    (let [captured-exception (atom nil)
          captured-request-uri (atom nil)
          handler (fn [_] (throw (ex-info "error message" {:code 42})))
          exc-handler (fn [exception request]
                        (reset! captured-exception exception)
                        (reset! captured-request-uri (:uri request))
                        {:status 500
                         :body "handled"})
          app (test-client {:routes ["/test-uri" {:get handler}]
                            :error-handlers {Exception exc-handler}})
          resp (app {:request-method :get
                     :uri "/test-uri"})]
      (is (match? {:status 500 :body "handled"} resp))
      (is (= "error message" (ex-message @captured-exception)))
      (is (= {:code 42} (ex-data @captured-exception)))
      (is (= "/test-uri" @captured-request-uri)))))

(deftest exception-handling-specificity
  (testing "more specific exception type handler is used"
    (let [handler (fn [_] (throw (IllegalArgumentException. "bad argument")))
          general-handler (fn [_exc _req]
                            {:status 500 :body "general handler"})
          specific-handler (fn [_exc _req]
                             {:status 400 :body "specific handler"})
          app (test-client {:routes ["/" {:get handler}]
                            :error-handlers {Exception general-handler
                                             IllegalArgumentException specific-handler}})
          resp (app {:request-method :get :uri "/"})]
      (is (match? {:status 400
                   :body "specific handler"}
                  resp)))))

(deftest exception-handling-runtime-exception
  (testing "handles RuntimeException without ex-info"
    (let [handler (fn [_] (throw (RuntimeException. "runtime error")))
          exc-handler (fn [exc _req]
                        {:status 500
                         :body (str "caught: " (.getMessage exc))})
          app (test-client {:routes ["/" {:get handler}]
                            :error-handlers {RuntimeException exc-handler}})
          resp (app {:request-method :get :uri "/"})]
      (is (match? {:status 500
                   :body "caught: runtime error"}
                  resp)))))

(deftest exception-handling-custom-status-codes
  (testing "error handler can return custom status codes"
    (let [handler (fn [_] (throw (ex-info "not found" {:type ::not-found})))
          not-found-handler (fn [_exc _req]
                              {:status 404
                               :body "Resource not found"})
          app (test-client {:routes ["/" {:get handler}]
                            :error-handlers {::not-found not-found-handler}})
          resp (app {:request-method :get :uri "/"})]
      (is (match? {:status 404
                   :body "Resource not found"}
                  resp))))

  (testing "error handler can return 4xx status codes"
    (let [handler (fn [_] (throw (ex-info "bad request" {:type ::validation-error})))
          validation-handler (fn [_exc _req]
                               {:status 422
                                :body "Validation failed"})
          app (test-client {:routes ["/" {:get handler}]
                            :error-handlers {::validation-error validation-handler}})
          resp (app {:request-method :get :uri "/"})]
      (is (match? {:status 422
                   :body "Validation failed"}
                  resp)))))

(deftest exception-handling-html-error-response
  (testing "error handler can return HTML response"
    (let [handler (fn [_] (throw (ex-info "error" {:type ::html-error})))
          html-error-handler (fn [_exc _req]
                               (z/html-response 500 [:div [:h1 "Error"] [:p "Something went wrong"]]))
          app (test-client {:routes ["/" {:get handler}]
                            :error-handlers {::html-error html-error-handler}})
          resp (app {:request-method :get :uri "/"})]
      (is (match? {:status 500
                   :headers {"content-type" "text/html"}
                   :body "<!DOCTYPE html><div><h1>Error</h1><p>Something went wrong</p></div>"}
                  resp)))))

(deftest exception-handling-json-error-response
  (testing "error handler can return JSON response"
    (let [handler (fn [_] (throw (ex-info "api error" {:type ::api-error :code "E001"})))
          json-error-handler (fn [exc _req]
                               (z/json-response 500 {:error "Internal error"
                                                     :code (:code (ex-data exc))}))
          app (test-client {:routes ["/" {:get handler}]
                            :error-handlers {::api-error json-error-handler}})
          resp (app {:request-method :get :uri "/"})]
      (is (match? {:status 500
                   :headers {"content-type" "application/json"}}
                  resp))
      (is (re-find #"E001" (:body resp))))))

(deftest exception-handling-multiple-types
  (testing "multiple exception types can have different handlers"
    (let [auth-handler (fn [_] (throw (ex-info "auth" {:type ::auth-error})))
          validation-handler (fn [_] (throw (ex-info "validation" {:type ::validation-error})))
          auth-error-handler (fn [_exc _req]
                               {:status 401 :body "Unauthorized"})
          validation-error-handler (fn [_exc _req]
                                     {:status 400 :body "Bad Request"})
          app (test-client {:routes [""
                                     ["/auth" {:get auth-handler}]
                                     ["/validate" {:get validation-handler}]]
                            :error-handlers {::auth-error auth-error-handler
                                             ::validation-error validation-error-handler}})
          auth-resp (app {:request-method :get :uri "/auth"})
          validation-resp (app {:request-method :get :uri "/validate"})]
      (is (match? {:status 401 :body "Unauthorized"} auth-resp))
      (is (match? {:status 400 :body "Bad Request"} validation-resp)))))

(deftest exception-handling-fallback-to-default
  (testing "falls back to default handler when no specific handler matches"
    (let [handler (fn [_] (throw (ex-info "unhandled" {:type ::unknown-type})))
          specific-handler (fn [_exc _req]
                             {:status 400 :body "Specific"})
          app (test-client {:routes ["/" {:get handler}]
                            :error-handlers {::known-type specific-handler}})
          resp (app {:request-method :get :uri "/"})]
      ;; Should fall back to default 500 "Unknown error" response
      (is (match? {:status 500
                   :body "Unknown error"}
                  resp)))))

;;; ==========================================================================
;;; Path Parameter Coercion Tests
;;; ==========================================================================

(deftest coercion
  (testing "coercion success"
    (let [handler (spy/spy (fn [_] {:status 200}))
          app (test-client {:routes ["/:x" {:name :root
                                            :handler handler
                                            :parameters {:path {:x int?}}}]})
          resp (app {:request-method :get
                     :uri "/1"})]
      (is (match? {:status 200} resp))))

  (testing "coercion error"
    (let [handler (spy/spy (fn [_] {:status 200}))
          app (test-client {:routes ["/:x" {:name :root
                                            :handler handler
                                            :parameters {:path {:x int?}}}]})
          resp (-> (app {:request-method :get
                         :uri "/abc"})
                   (update :body (comp charred/read-json slurp)))]
      (is (match? {:status 400
                   :body {"value" {"x" "abc"}
                          "type" "reitit.coercion/request-coercion"
                          "coercion" "malli"
                          "in" ["request" "path-params"]
                          "humanized" {"x" ["should be an int"]}}}
                  resp)))))

;;; ==========================================================================
;;; Anti-Forgery (CSRF) Tests
;;; ==========================================================================

(deftest anti-forgery
  (let [post-handler (fn [{:keys [] :as _request}]
                       [:div "hi"])
        app (test-client {:routes [""
                                   ["/" {:get csrf-handler
                                         :post post-handler}]
                                   ["/whitelisted" {:get csrf-handler
                                                    :post post-handler}]]
                          :anti-forgery-whitelist ["/whitelisted"]})]
    (testing "anti-forgery token required"
      (let [sess (-> (peri/session app) ;; Get the CSRF token
                     (peri/request "/"))
            token (-> sess :response :body)
            resp (-> sess
                     ;; POST with token
                     (peri/request "/"
                                   :request-method :post
                                   :headers {"X-CSRF-Token" token}
                                   :content-type "text/html"
                                   :body "anything")
                     :response)]
        (is (match? {:status 200
                     :headers {"content-type" "text/html"}
                     :body "<!DOCTYPE html><div>hi</div>"}
                    resp))))

    (testing "anti-forgery missing - 403"
      (let [resp (-> (peri/session app)
                     ;; POST without token
                     (peri/request "/"
                                   :request-method :post
                                   :content-type "text/html"
                                   :body "anything")
                     :response)]
        (is (match? {:status 403
                     :body "<h1>Invalid anti-forgery token</h1>"}
                    resp))))

    (testing "anti-forgery - whitelist"
      (let [resp (-> (peri/session app)
                     ;; POST without token to whitelist uri
                     (peri/request "/whitelisted"
                                   :request-method :post
                                   :content-type "text/html"
                                   :body "anything")
                     :response)]
        (is (match? {:status 200
                     :headers {"content-type" "text/html"}
                     :body "<!DOCTYPE html><div>hi</div>"} resp))))))

;;; ==========================================================================
;;; Options Schema Validation Tests
;;; ==========================================================================

(deftest options-schems
  (testing "Options - all fields optional"
    (is (m/validate z/Options {})))
  (testing "Options :anti-forgery-whitelist"
    (is (m/validate z/Options {:anti-forgery-whitelist ["test"]}))
    (is (m/validate z/Options {:anti-forgery-whitelist [#"test"]}))
    (is (not (m/validate z/Options {:anti-forgery-whitelist [:xxx]}))))
  (testing "Options :cookie-attrs"
    (is (m/validate z/Options {:cookie-attrs {:something "test"}}))
    (is (not (m/validate z/Options {:cookie-attrs :xxx}))))
  (testing "Options :cookie-name"
    (is (m/validate z/Options {:cookie-name "my-session"}))
    (is (not (m/validate z/Options {:cookie-name 123}))))
  (testing "Options :cookie-secret"
    (is (m/validate z/Options {:cookie-secret "0123456789abcdef"}))
    (is (not (m/validate z/Options {:cookie-secret "1234"})))
    (is (m/validate z/Options {:cookie-secret (.getBytes "0123456789abcdef")}))
    (is (not (m/validate z/Options {:cookie-secret (.getBytes "1234")}))))
  (testing "Options :error-handler"
    (is (m/validate z/Options {:error-handler {:xxx (fn [])}}))
    (is (not (m/validate z/Options {:extensions "something"}))))
  (testing "Options :extensions"
    (is (m/validate z/Options {:extensions [(fn [])]}))
    (is (not (m/validate z/Options {:extensions ["xxx"]})))
    #_(is (not (m/validate z/Options {:extensions [:xxx]})))
    (is (not (m/validate z/Options {:extensions :xxx}))))
  (testing "Options :middleware"
    (is (m/validate z/Options {:middleware [(fn [])]}))
    (is (m/validate z/Options {:middleware []}))
    (is (not (m/validate z/Options {:middleware "invalid value"}))))
  (testing "Options :port"
    (is (m/validate z/Options {:port 1234}))
    (is (not (m/validate z/Options {:port "1234"})))
    (is (not (m/validate z/Options {:port nil})))
    (is (not (m/validate z/Options {:port :xxx}))))
  (testing "Options :reload-per-request?"
    (is (m/validate z/Options {:reload-per-request? true}))
    (is (not (m/validate z/Options {:reload-per-request? nil})))
    (is (not (m/validate z/Options {:reload-per-request? :xxx}))))
  (testing "Options :request-context"
    (is (m/validate z/Options {:request-context {:somekey 1234}}))
    (is (not (m/validate z/Options {:request-context :xxx}))))
  (testing "Options :routes"
    (is (m/validate z/Options {:routes ["/" {:handler (constantly {})}]}))
    (is (not (m/validate z/Options {:routes :xxx}))))
  (testing "Options :start-server?"
    (is (m/validate z/Options {:start-server? true}))
    (is (not (m/validate z/Options {:start-server? nil})))))

;;; ==========================================================================
;;; Session & Flash Message Tests
;;; ==========================================================================

(deftest session-persistence-across-requests
  (testing "session data persists across multiple requests"
    (let [set-handler (fn [_]
                        {:status 200
                         :body "set"
                         :session {:user-id 123}})
          get-handler (fn [{:keys [session]}]
                        {:status 200
                         :body (str (:user-id session))})
          app (test-client {:routes [""
                                     ["/set" {:get set-handler}]
                                     ["/get" {:get get-handler}]]})
          resp (-> (peri/session app)
                   (peri/request "/set")
                   (peri/request "/get")
                   :response)]
      (is (match? {:status 200
                   :body "123"}
                  resp)))))

(deftest session-data-modification
  (testing "session data can be updated across requests"
    (let [increment-handler (fn [{:keys [session]}]
                              (let [count (inc (or (:count session) 0))]
                                {:status 200
                                 :body (str count)
                                 :session {:count count}}))
          app (test-client {:routes ["/" {:get increment-handler}]})
          sess (-> (peri/session app)
                   (peri/request "/")
                   (peri/request "/")
                   (peri/request "/"))]
      (is (match? {:status 200 :body "3"}
                  (:response sess))))))

(deftest session-data-deletion
  (testing "session keys can be removed"
    (let [set-handler (fn [_]
                        {:status 200
                         :body "set"
                         :session {:temp-data "temporary" :keep-data "permanent"}})
          delete-handler (fn [{:keys [session]}]
                           {:status 200
                            :body "deleted"
                            :session (dissoc session :temp-data)})
          get-handler (fn [{:keys [session]}]
                        {:status 200
                         :body (str "temp=" (:temp-data session "nil")
                                    ",keep=" (:keep-data session "nil"))})
          app (test-client {:routes [""
                                     ["/set" {:get set-handler}]
                                     ["/delete" {:get delete-handler}]
                                     ["/get" {:get get-handler}]]})
          resp (-> (peri/session app)
                   (peri/request "/set")
                   (peri/request "/delete")
                   (peri/request "/get")
                   :response)]
      (is (match? {:status 200
                   :body "temp=nil,keep=permanent"}
                  resp)))))

(deftest session-binding-in-handler
  (testing "*session* dynamic var is correctly bound"
    (let [captured-session (atom nil)
          handler (fn [{:keys [session]}]
                    (reset! captured-session z/*session*)
                    {:status 200
                     :body "ok"
                     :session {:test-key "test-value"}})
          app (test-client {:routes [""
                                     ["/set" {:get (fn [_]
                                                     {:status 200
                                                      :body "set"
                                                      :session {:existing "data"}})}]
                                     ["/check" {:get handler}]]})]
      (-> (peri/session app)
          (peri/request "/set")
          (peri/request "/check"))
      (is (match? {:existing "data"} @captured-session)))))

(deftest flash-message-set-and-read
  (testing "flash messages set in one request are readable in next"
    (let [set-handler (fn [_]
                        {:status 200
                         :body "set"
                         :flash {:message "Success!"}})
          get-handler (fn [{:keys [flash]}]
                        {:status 200
                         :body (str (:message flash))})
          app (test-client {:routes [""
                                     ["/set" {:get set-handler}]
                                     ["/get" {:get get-handler}]]})
          resp (-> (peri/session app)
                   (peri/request "/set")
                   (peri/request "/get")
                   :response)]
      (is (match? {:status 200
                   :body "Success!"}
                  resp)))))

(deftest flash-message-cleared-after-read
  (testing "flash messages are cleared after being read"
    (let [set-handler (fn [_]
                        {:status 200
                         :body "set"
                         :flash {:message "Temporary!"}})
          get-handler (fn [{:keys [flash]}]
                        {:status 200
                         :body (str "flash=" (:message flash "nil"))})
          app (test-client {:routes [""
                                     ["/set" {:get set-handler}]
                                     ["/get" {:get get-handler}]]})
          sess (-> (peri/session app)
                   (peri/request "/set")
                   (peri/request "/get"))
          first-read (:response sess)
          second-read (-> sess
                          (peri/request "/get")
                          :response)]
      (is (match? {:body "flash=Temporary!"} first-read))
      (is (match? {:body "flash=nil"} second-read)))))

(deftest flash-message-with-redirect
  (testing "flash messages work with redirect (PRG pattern)"
    (let [post-handler (fn [_]
                         {:status 302
                          :headers {"Location" "/result"}
                          :flash {:notice "Created successfully!"}})
          result-handler (fn [{:keys [flash]}]
                           {:status 200
                            :body (str (:notice flash))})
          app (test-client {:routes [""
                                     ["/create" {:get csrf-handler
                                                 :post post-handler}]
                                     ["/result" {:get result-handler}]]})
          sess (-> (peri/session app)
                   (peri/request "/create"))
          token (-> sess :response :body)
          resp (-> sess
                   (peri/request "/create"
                                 :request-method :post
                                 :headers {"X-CSRF-Token" token})
                   (peri/follow-redirect)
                   :response)]
      (is (match? {:status 200
                   :body "Created successfully!"}
                  resp)))))

(deftest multiple-flash-keys
  (testing "multiple flash values can be set and read"
    (let [set-handler (fn [_]
                        {:status 200
                         :body "set"
                         :flash {:success "Item created"
                                 :info "ID: 123"
                                 :warning "Review pending"}})
          get-handler (fn [{:keys [flash]}]
                        {:status 200
                         :body (str "success=" (:success flash)
                                    ",info=" (:info flash)
                                    ",warning=" (:warning flash))})
          app (test-client {:routes [""
                                     ["/set" {:get set-handler}]
                                     ["/get" {:get get-handler}]]})
          resp (-> (peri/session app)
                   (peri/request "/set")
                   (peri/request "/get")
                   :response)]
      (is (match? {:status 200
                   :body "success=Item created,info=ID: 123,warning=Review pending"}
                  resp)))))

;;; ==========================================================================
;;; Cookie Tests
;;; ==========================================================================

(deftest set-cookie-in-response
  (testing "cookies can be set in responses"
    (let [handler (fn [_]
                    {:status 200
                     :body "ok"
                     :cookies {"mycookie" {:value "hello"}}})
          app (test-client {:routes ["/" {:get handler}]})
          resp (-> (peri/session app)
                   (peri/request "/")
                   :response)]
      (is (match? {:status 200} resp))
      (is (some #(re-find #"mycookie=hello" %)
                (get-in resp [:headers "Set-Cookie"]))))))

(deftest read-cookie-from-request
  (testing "cookies are available in request map"
    (let [set-handler (fn [_]
                        {:status 200
                         :body "set"
                         :cookies {"user-pref" {:value "dark-mode"}}})
          read-handler (fn [{:keys [cookies]}]
                         {:status 200
                          :body (get-in cookies ["user-pref" :value] "not-found")})
          app (test-client {:routes [""
                                     ["/set" {:get set-handler}]
                                     ["/read" {:get read-handler}]]})
          resp (-> (peri/session app)
                   (peri/request "/set")
                   (peri/request "/read")
                   :response)]
      (is (match? {:status 200
                   :body "dark-mode"}
                  resp)))))

(deftest cookie-attributes-http-only
  (testing "http-only attribute is set correctly"
    (let [handler (fn [_]
                    {:status 200
                     :body "ok"
                     :cookies {"secure-cookie" {:value "secret"
                                                :http-only true}}})
          app (test-client {:routes ["/" {:get handler}]})
          resp (-> (peri/session app)
                   (peri/request "/")
                   :response)
          set-cookie-headers (get-in resp [:headers "Set-Cookie"])]
      (is (some #(and (re-find #"secure-cookie=secret" %)
                      (re-find #"(?i)httponly" %))
                set-cookie-headers)))))

(deftest cookie-attributes-same-site
  (testing "same-site attribute is configurable"
    (let [handler (fn [_]
                    {:status 200
                     :body "ok"
                     :cookies {"csrf-cookie" {:value "token123"
                                              :same-site :strict}}})
          app (test-client {:routes ["/" {:get handler}]})
          resp (-> (peri/session app)
                   (peri/request "/")
                   :response)
          set-cookie-headers (get-in resp [:headers "Set-Cookie"])]
      (is (some #(and (re-find #"csrf-cookie=token123" %)
                      (re-find #"(?i)samesite=strict" %))
                set-cookie-headers)))))

(deftest cookie-attributes-max-age-and-path
  (testing "max-age and path attributes are set"
    (let [handler (fn [_]
                    {:status 200
                     :body "ok"
                     :cookies {"pref-cookie" {:value "setting"
                                              :max-age 3600
                                              :path "/api"}}})
          app (test-client {:routes ["/" {:get handler}]})
          resp (-> (peri/session app)
                   (peri/request "/")
                   :response)
          set-cookie-headers (get-in resp [:headers "Set-Cookie"])]
      (is (some #(and (re-find #"pref-cookie=setting" %)
                      (re-find #"(?i)max-age=3600" %)
                      (re-find #"(?i)path=/api" %))
                set-cookie-headers)))))

(deftest custom-cookie-name
  (testing "session cookie uses custom name when :cookie-name is set"
    (let [handler (fn [req]
                    (let [counter (get-in req [:session :counter] 0)]
                      {:status 200
                       :body (str counter)
                       :session {:counter (inc counter)}}))
          app (test-client {:routes ["/" {:get handler}]
                            :cookie-name "my-session"})
          resp (-> (peri/session app)
                   (peri/request "/")
                   :response)]
      (is (some #(re-find #"my-session=" %)
                (get-in resp [:headers "Set-Cookie"])))
      (is (not (some #(re-find #"ring-session=" %)
                     (get-in resp [:headers "Set-Cookie"])))))))

;;; ==========================================================================
;;; Dynamic Variables Tests
;;; ==========================================================================

(deftest request-binding-access
  (testing "*request* dynamic var is bound during request handling"
    (let [captured-uri (atom nil)
          handler (fn [_]
                    (reset! captured-uri (:uri z/*request*))
                    {:status 200 :body "ok"})
          app (test-client {:routes ["/test-path" {:get handler}]})]
      (app {:request-method :get :uri "/test-path"})
      (is (= "/test-path" @captured-uri)))))

(deftest router-binding-access
  (testing "*router* binding allows url-for in handlers"
    (let [handler (fn [_]
                    {:status 200
                     :body (z/url-for :other-route)})
          app (test-client {:routes [""
                                     ["/" {:name :root
                                           :get handler}]
                                     ["/other" {:name :other-route
                                                :get (constantly {:status 200})}]]})
          resp (app {:request-method :get :uri "/"})]
      (is (match? {:status 200
                   :body "/other"}
                  resp)))))

(deftest session-binding-access
  (testing "*session* binding matches request session"
    (let [sessions-match (atom false)
          handler (fn [{:keys [session]}]
                    (reset! sessions-match (= session z/*session*))
                    {:status 200
                     :body "ok"
                     :session {:checked true}})
          app (test-client {:routes [""
                                     ["/set" {:get (fn [_]
                                                     {:status 200
                                                      :body "set"
                                                      :session {:user "test"}})}]
                                     ["/check" {:get handler}]]})]
      (-> (peri/session app)
          (peri/request "/set")
          (peri/request "/check"))
      (is @sessions-match))))

(deftest bindings-available-in-nested-calls
  (testing "bindings propagate to helper functions"
    (let [get-uri-from-binding (fn [] (:uri z/*request*))
          handler (fn [_]
                    {:status 200
                     :body (get-uri-from-binding)})
          app (test-client {:routes ["/nested-test" {:get handler}]})
          resp (app {:request-method :get :uri "/nested-test"})]
      (is (match? {:status 200
                   :body "/nested-test"}
                  resp)))))

;;; ==========================================================================
;;; Query Parameter Tests
;;; ==========================================================================

(deftest query-string-parsing
  (testing "basic query string parsing"
    (let [handler (fn [{:keys [query-params]}]
                    {:status 200
                     :body (str "name=" (get query-params "name")
                                ",age=" (get query-params "age"))})
          app (test-client {:routes ["/" {:get handler}]})
          resp (app {:request-method :get
                     :uri "/"
                     :query-string "name=john&age=30"})]
      (is (match? {:status 200
                   :body "name=john,age=30"}
                  resp)))))

(deftest query-parameter-coercion-success
  (testing "query parameters are coerced according to schema"
    (let [handler (spy/spy (fn [{:keys [parameters]}]
                             {:status 200
                              :body (str (get-in parameters [:query :age]))}))
          app (test-client {:routes ["/" {:get handler
                                          :parameters {:query {:age int?}}}]})
          resp (app {:request-method :get
                     :uri "/"
                     :query-string "age=30"})]
      (is (match? {:status 200 :body "30"} resp))
      (is (int? (get-in (first (spy/first-call handler)) [:parameters :query :age]))))))

(deftest query-parameter-coercion-error
  (testing "invalid query param type returns 400"
    (let [handler (fn [_] {:status 200})
          app (test-client {:routes ["/" {:get handler
                                          :parameters {:query {:age int?}}}]})
          resp (-> (app {:request-method :get
                         :uri "/"
                         :query-string "age=not-a-number"})
                   (update :body (comp charred/read-json slurp)))]
      (is (match? {:status 400
                   :body {"type" "reitit.coercion/request-coercion"
                          "coercion" "malli"
                          "in" ["request" "query-params"]}}
                  resp)))))

(deftest optional-query-parameters
  (testing "optional query params don't cause errors when missing"
    (let [handler (fn [{:keys [parameters]}]
                    {:status 200
                     :body (str "name=" (get-in parameters [:query :name])
                                ",age=" (get-in parameters [:query :age] "default"))})
          app (test-client {:routes ["/" {:get handler
                                          :parameters {:query [:map
                                                               [:name string?]
                                                               [:age {:optional true} int?]]}}]})
          resp (app {:request-method :get
                     :uri "/"
                     :query-string "name=john"})]
      (is (match? {:status 200
                   :body "name=john,age=default"}
                  resp)))))

(deftest query-params-with-array-values
  (testing "multi-value query parameters are collected into a vector"
    (let [handler (fn [{:keys [query-params]}]
                    {:status 200
                     :body (pr-str (get query-params "tags"))})
          app (test-client {:routes ["/" {:get handler}]})
          resp (app {:request-method :get
                     :uri "/"
                     :query-string "tags=a&tags=b&tags=c"})]
      (is (match? {:status 200
                   :body "[\"a\" \"b\" \"c\"]"}
                  resp)))))

;;; ==========================================================================
;;; Request/Response Coercion Tests
;;; ==========================================================================

(deftest response-body-coercion
  (testing "response body coercion with Malli schemas"
    (let [handler (fn [_]
                    {:status 200
                     :body {:id 123 :name "test"}})
          app (test-client {:routes ["/" {:get handler
                                          :responses {200 {:body [:map
                                                                  [:id int?]
                                                                  [:name string?]]}}}]})
          resp (app {:request-method :get
                     :uri "/"
                     :headers {"accept" "application/json"}})]
      (is (match? {:status 200} resp)))))

(deftest multiple-parameter-types-combined
  (testing "path + query + body coercion together"
    (let [handler (spy/spy (fn [{:keys [parameters]}]
                             {:status 200
                              :body (str "id=" (get-in parameters [:path :id])
                                         ",expand=" (get-in parameters [:query :expand])
                                         ",data=" (get-in parameters [:body :data]))}))
          app (test-client {:routes ["/:id" {:get csrf-handler
                                             :post {:handler handler
                                                    :parameters {:path {:id int?}
                                                                 :query {:expand boolean?}
                                                                 :body [:map [:data string?]]}}}]
                            :anti-forgery-whitelist [#"/\d+"]})
          resp (-> (peri/session app)
                   (peri/request "/123?expand=true"
                                 :request-method :post
                                 :content-type "application/json"
                                 :body "{\"data\": \"hello\"}")
                   :response)]
      (is (match? {:status 200
                   :body "id=123,expand=true,data=hello"}
                  resp))
      (let [params (get (first (spy/first-call handler)) :parameters)]
        (is (int? (get-in params [:path :id])))
        (is (boolean? (get-in params [:query :expand])))))))

(deftest required-vs-optional-parameters
  (testing "required parameter enforcement returns 400"
    (let [handler (fn [_] {:status 200})
          app (test-client {:routes ["/" {:get handler
                                          :parameters {:query [:map
                                                               [:required-param string?]]}}]})
          resp (-> (app {:request-method :get
                         :uri "/"
                         :query-string ""})
                   (update :body (comp charred/read-json slurp)))]
      (is (match? {:status 400
                   :body {"type" "reitit.coercion/request-coercion"}}
                  resp)))))

(deftest nested-schema-coercion
  (testing "complex nested schema coercion"
    (let [handler (spy/spy (fn [{:keys [parameters]}]
                             {:status 200
                              :body (get-in parameters [:body :user :address :city])}))
          app (test-client {:routes ["/" {:get csrf-handler
                                          :post {:handler handler
                                                 :parameters {:body [:map
                                                                     [:user [:map
                                                                             [:name string?]
                                                                             [:address [:map
                                                                                        [:city string?]
                                                                                        [:zip string?]]]]]]}}}]
                            :anti-forgery-whitelist ["/"]})
          resp (-> (peri/session app)
                   (peri/request "/"
                                 :request-method :post
                                 :content-type "application/json"
                                 :body "{\"user\": {\"name\": \"John\", \"address\": {\"city\": \"NYC\", \"zip\": \"10001\"}}}")
                   :response)]
      (is (match? {:status 200
                   :body "NYC"}
                  resp)))))

(deftest coercion-with-default-values
  (testing "Malli default value handling"
    (let [handler (fn [{:keys [parameters]}]
                    {:status 200
                     :body (str "page=" (get-in parameters [:query :page])
                                ",limit=" (get-in parameters [:query :limit]))})
          app (test-client {:routes ["/" {:get handler
                                          :parameters {:query [:map
                                                               [:page {:default 1} int?]
                                                               [:limit {:default 10} int?]]}}]})
          resp (app {:request-method :get
                     :uri "/"
                     :query-string ""})]
      (is (match? {:status 200
                   :body "page=1,limit=10"}
                  resp)))))

;;; ==========================================================================
;;; Content Negotiation Tests
;;; ==========================================================================

(deftest accept-header-json-response
  (testing "JSON response when Accept: application/json"
    (let [handler (fn [_]
                    {:status 200
                     :body {:message "hello"}})
          app (test-client {:routes ["/" {:get handler}]})
          resp (app {:request-method :get
                     :uri "/"
                     :headers {"accept" "application/json"}})]
      (is (match? {:status 200} resp))
      (is (or (re-find #"application/json" (get-in resp [:headers "Content-Type"] ""))
              ;; Body may be returned as-is if not explicitly encoded
              (map? (:body resp)))))))

(deftest content-type-auto-detection
  (testing "response format based on return type"
    (let [html-handler (fn [_] [:p "html"])
          json-handler (fn [_] (z/json-response {:type "json"}))
          app (test-client {:routes [""
                                     ["/html" {:get html-handler}]
                                     ["/json" {:get json-handler}]]})
          html-resp (app {:request-method :get :uri "/html"})
          json-resp (app {:request-method :get :uri "/json"})]
      (is (match? {:headers {"content-type" "text/html"}} html-resp))
      (is (match? {:headers {"content-type" "application/json"}} json-resp)))))

(deftest response-encoding-charset
  (testing "UTF-8 charset in responses with unicode"
    (let [handler (fn [_]
                    {:status 200
                     :headers {"content-type" "text/plain; charset=utf-8"}
                     :body "Hello 世界 🌍"})
          app (test-client {:routes ["/" {:get handler}]})
          resp (app {:request-method :get :uri "/"})]
      (is (match? {:status 200
                   :body "Hello 世界 🌍"}
                  resp)))))

;;; ==========================================================================
;;; Configuration & Extensions Tests
;;; ==========================================================================

(deftest multiple-extensions-compose
  (testing "multiple extensions can be composed"
    (let [handler (spy/spy (fn [_] {:status 200}))
          ext1 (fn [cfg]
                 (update-in cfg [::z/middleware :context] assoc :db "database"))
          ext2 (fn [cfg]
                 (update-in cfg [::z/middleware :context] assoc :cache "redis"))
          app (test-client {:routes ["/" {:get handler}]
                            :extensions [ext1 ext2]})]
      (app {:request-method :get :uri "/"})
      (is (match? {::z/context {:db "database"
                                :cache "redis"}}
                  (first (spy/first-call handler)))))))

(deftest extension-order-matters
  (testing "later extensions override earlier ones for same key"
    (let [handler (spy/spy (fn [_] {:status 200}))
          ext1 (fn [cfg]
                 (update-in cfg [::z/middleware :context] assoc :value "first"))
          ext2 (fn [cfg]
                 (update-in cfg [::z/middleware :context] assoc :value "second"))
          app (test-client {:routes ["/" {:get handler}]
                            :extensions [ext1 ext2]})]
      (app {:request-method :get :uri "/"})
      (is (match? {::z/context {:value "second"}}
                  (first (spy/first-call handler)))))))

(deftest custom-middleware-injection
  (testing "custom middleware via :middleware option"
    (let [custom-middleware (fn [handler]
                              (fn [request]
                                (let [response (handler request)]
                                  (assoc-in response [:headers "X-Custom-Header"] "custom-value"))))
          app-handler (fn [_] {:status 200 :body "ok"})
          app (test-client {:routes ["/" {:get app-handler
                                          :middleware [custom-middleware]}]})
          resp (app {:request-method :get :uri "/"})]
      (is (match? {:status 200
                   :headers {"X-Custom-Header" "custom-value"}}
                  resp)))))

;;; ==========================================================================
;;; Invalid Parameter Key Tests
;;; ==========================================================================

(deftest json-body-with-invalid-keyword-keys
  (testing "JSON body with key starting with number"
    (let [handler (fn [{:keys [body-params]}]
                    {:status 200
                     :body (pr-str body-params)})
          app (test-client {:routes ["/" {:get csrf-handler
                                          :post handler}]
                            :anti-forgery-whitelist ["/"]})
          resp (-> (peri/session app)
                   (peri/request "/"
                                 :request-method :post
                                 :content-type "application/json"
                                 :body "{\"123\": \"value\", \"normal\": \"ok\"}")
                   :response)]
      (is (match? {:status 200} resp))
      ;; Check that both keys are accessible (charred keywordizes them)
      (is (re-find #":123" (:body resp)))
      (is (re-find #":normal" (:body resp)))))

  (testing "JSON body with key containing spaces"
    (let [handler (fn [{:keys [body-params]}]
                    {:status 200
                     :body (pr-str body-params)})
          app (test-client {:routes ["/" {:get csrf-handler
                                          :post handler}]
                            :anti-forgery-whitelist ["/"]})
          resp (-> (peri/session app)
                   (peri/request "/"
                                 :request-method :post
                                 :content-type "application/json"
                                 :body "{\"key with spaces\": \"value\"}")
                   :response)]
      (is (match? {:status 200} resp))
      ;; Clojure allows keywords with spaces when created via `keyword` fn
      (is (re-find #"key with spaces" (:body resp)))))

  (testing "JSON body with empty string key"
    (let [handler (fn [{:keys [body-params]}]
                    {:status 200
                     :body (pr-str body-params)})
          app (test-client {:routes ["/" {:get csrf-handler
                                          :post handler}]
                            :anti-forgery-whitelist ["/"]})
          resp (-> (peri/session app)
                   (peri/request "/"
                                 :request-method :post
                                 :content-type "application/json"
                                 :body "{\"\": \"empty-key-value\"}")
                   :response)]
      (is (match? {:status 200} resp))
      ;; Empty string becomes keyword via (keyword "")
      (is (re-find #"empty-key-value" (:body resp))))))

(deftest query-params-with-invalid-keyword-keys
  (testing "query param with key starting with number"
    (let [handler (fn [{:keys [query-params]}]
                    {:status 200
                     :body (pr-str query-params)})
          app (test-client {:routes ["/" {:get handler}]})
          resp (app {:request-method :get
                     :uri "/"
                     :query-string "123=value&normal=ok"})]
      (is (match? {:status 200} resp))
      ;; Query params use string keys by default
      (is (re-find #"\"123\"" (:body resp)))
      (is (re-find #"\"normal\"" (:body resp)))))

  (testing "query param with key containing spaces (URL encoded)"
    (let [handler (fn [{:keys [query-params]}]
                    {:status 200
                     :body (pr-str query-params)})
          app (test-client {:routes ["/" {:get handler}]})
          resp (app {:request-method :get
                     :uri "/"
                     :query-string "key%20with%20spaces=value"})]
      (is (match? {:status 200} resp))
      (is (re-find #"key with spaces" (:body resp)))))

  (testing "query param with empty key"
    (let [handler (fn [{:keys [query-params]}]
                    {:status 200
                     :body (pr-str query-params)})
          app (test-client {:routes ["/" {:get handler}]})
          resp (app {:request-method :get
                     :uri "/"
                     :query-string "=empty-key-value"})]
      (is (match? {:status 200} resp))
      (is (re-find #"empty-key-value" (:body resp))))))

(deftest form-params-with-invalid-keyword-keys
  (testing "form param with key starting with number"
    (let [handler (fn [{:keys [form-params]}]
                    {:status 200
                     :body (pr-str form-params)})
          app (test-client {:routes ["/" {:get csrf-handler
                                          :post handler}]})
          sess (-> (peri/session app)
                   (peri/request "/"))
          token (-> sess :response :body)
          resp (-> sess
                   (peri/request "/"
                                 :request-method :post
                                 :headers {"X-CSRF-Token" token}
                                 :content-type "application/x-www-form-urlencoded"
                                 :body "123=value&normal=ok")
                   :response)]
      (is (match? {:status 200} resp))
      ;; Form params use string keys
      (is (re-find #"\"123\"" (:body resp)))
      (is (re-find #"\"normal\"" (:body resp)))))

  (testing "form param with key containing spaces (URL encoded)"
    (let [handler (fn [{:keys [form-params]}]
                    {:status 200
                     :body (pr-str form-params)})
          app (test-client {:routes ["/" {:get csrf-handler
                                          :post handler}]})
          sess (-> (peri/session app)
                   (peri/request "/"))
          token (-> sess :response :body)
          resp (-> sess
                   (peri/request "/"
                                 :request-method :post
                                 :headers {"X-CSRF-Token" token}
                                 :content-type "application/x-www-form-urlencoded"
                                 :body "key%20with%20spaces=value")
                   :response)]
      (is (match? {:status 200} resp))
      (is (re-find #"key with spaces" (:body resp)))))

  (testing "form param with empty key"
    (let [handler (fn [{:keys [form-params]}]
                    {:status 200
                     :body (pr-str form-params)})
          app (test-client {:routes ["/" {:get csrf-handler
                                          :post handler}]})
          sess (-> (peri/session app)
                   (peri/request "/"))
          token (-> sess :response :body)
          resp (-> sess
                   (peri/request "/"
                                 :request-method :post
                                 :headers {"X-CSRF-Token" token}
                                 :content-type "application/x-www-form-urlencoded"
                                 :body "=empty-key-value")
                   :response)]
      (is (match? {:status 200} resp))
      (is (re-find #"empty-key-value" (:body resp))))))

;;; ==========================================================================
;;; Startup Error Handling Tests
;;; ==========================================================================

(deftest startup-invalid-options-throws
  (testing "invalid options throws exception instead of returning nil"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"Invalid Zodiac options"
                          (z/start {:port "not-an-int"}))))

  (testing "exception contains validation errors"
    (try
      (z/start {:port "not-an-int"})
      (catch clojure.lang.ExceptionInfo e
        (is (contains? (ex-data e) :validation-errors))))))

(deftest startup-failure-rolls-back-components
  (testing "components are rolled back when a later component fails"
    (let [started-components (atom [])
          stopped-components (atom [])
          ;; Extension that adds a component which tracks start/stop
          tracking-ext (fn [cfg]
                         (assoc cfg
                                ::test-component-a {:name :a
                                                    :started started-components
                                                    :stopped stopped-components}
                                ::test-component-b {:name :b
                                                    :dep (ig/ref ::test-component-a)
                                                    :started started-components
                                                    :stopped stopped-components}))
          ;; Extension that adds a failing component that depends on test-component-b
          failing-ext (fn [cfg]
                        (assoc cfg
                               ::failing-component {:dep (ig/ref ::test-component-b)}))]

      ;; Define init-key for our test components
      (defmethod ig/init-key ::test-component-a [_ {:keys [name started]}]
        (swap! started conj name)
        {:name name :stopped stopped-components})

      (defmethod ig/init-key ::test-component-b [_ {:keys [name started]}]
        (swap! started conj name)
        {:name name :stopped stopped-components})

      (defmethod ig/halt-key! ::test-component-a [_ {:keys [name stopped]}]
        (swap! stopped conj name))

      (defmethod ig/halt-key! ::test-component-b [_ {:keys [name stopped]}]
        (swap! stopped conj name))

      ;; Define init-key that throws
      (defmethod ig/init-key ::failing-component [_ _]
        (throw (ex-info "Component failed to start" {:reason :test-failure})))

      ;; Verify exception is thrown
      (is (thrown? clojure.lang.ExceptionInfo
                   (z/start {:routes ["/" {:get (constantly {:status 200})}]
                             :start-server? false
                             :cookie-secret "1234567890123456"
                             :extensions [tracking-ext failing-ext]})))

      ;; Verify both test components were started in order (a before b due to dependency)
      (is (= [:a :b] @started-components))

      ;; Verify both test components were stopped in reverse order (b before a)
      (is (= [:b :a] @stopped-components))

      ;; Clean up the defmethods
      (remove-method ig/init-key ::test-component-a)
      (remove-method ig/init-key ::test-component-b)
      (remove-method ig/halt-key! ::test-component-a)
      (remove-method ig/halt-key! ::test-component-b)
      (remove-method ig/init-key ::failing-component))))
