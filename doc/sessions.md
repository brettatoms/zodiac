# Sessions & Flash Messages

Zodiac provides cookie-based sessions and flash messages out of the box.

## Sessions

### Reading Session Data

Session data is available in the request map under `:session`:

```clojure
(defn handler [{:keys [session]}]
  (let [user-id (:user-id session)]
    {:status 200
     :body (str "Hello, user " user-id)}))
```

### Writing Session Data

Include `:session` in your response to update the session:

```clojure
(defn login-handler [request]
  {:status 200
   :body "Logged in"
   :session {:user-id 123}})
```

To update specific keys while preserving others:

```clojure
(defn update-handler [{:keys [session]}]
  {:status 200
   :body "Updated"
   :session (assoc session :last-seen (System/currentTimeMillis))})
```

To remove a key:

```clojure
(defn logout-handler [{:keys [session]}]
  {:status 200
   :body "Logged out"
   :session (dissoc session :user-id)})
```

### Dynamic Variable

The current session is also bound to `zodiac.core/*session*`:

```clojure
(require '[zodiac.core :as z])

(defn some-helper []
  ;; Access session without passing it explicitly
  (:user-id z/*session*))
```

### Cookie Configuration

Configure session cookies via start options:

```clojure
(z/start {:routes routes
          :cookie-secret "16-char-secret!"  ;; Required: exactly 16 characters
          :cookie-attrs {:http-only true    ;; Default: true
                         :same-site :lax    ;; Default: :lax
                         :secure true       ;; For HTTPS
                         :max-age 86400}})  ;; Seconds
```

## Flash Messages

Flash messages are session data that persists for exactly one request—useful for showing notifications after redirects.

### Setting Flash

Include `:flash` in your response:

```clojure
(defn create-handler [request]
  {:status 302
   :headers {"Location" "/items"}
   :flash {:success "Item created!"}})
```

### Reading Flash

Flash data appears in the next request under `:flash`:

```clojure
(defn list-handler [{:keys [flash]}]
  [:div
   (when-let [msg (:success flash)]
     [:div.alert msg])
   [:ul
    ;; list items...
    ]])
```

After being read, flash data is automatically cleared.

### Post-Redirect-Get Pattern

Flash is commonly used with the PRG pattern:

```clojure
(def routes
  ["/items"
   ["" {:get list-handler}]
   ["/new" {:get new-form
            :post create-handler}]])

(defn create-handler [{:keys [params]}]
  ;; ... create item ...
  {:status 302
   :headers {"Location" "/items"}
   :flash {:success "Item created successfully!"}})

(defn list-handler [{:keys [flash]}]
  [:div
   (when (:success flash)
     [:div.notification (:success flash)])
   ;; ... render list ...
   ])
```
