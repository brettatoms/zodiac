(ns zodiac.core-test
  (:require [clojure.data.json :as json]
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

(deftest coercion
  (testing "coercion error"
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
                   (update :body (comp json/read-str slurp)))]
      (is (match? {:status 400
                   :body {"value" {"x" "abc"}
                          "type" "reitit.coercion/request-coercion"
                          "coercion" "malli"
                          "in" ["request" "path-params"]
                          "humanized" {"x" ["should be an int"]}}}
                  resp)))))
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
