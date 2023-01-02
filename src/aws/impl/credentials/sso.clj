(ns aws.impl.credentials.sso
  (:require
    [babashka.curl :as curl]
    [cheshire.core :as json]))


(defn expired-within?
  [inst seconds]
  (neg? (- (.getSeconds (java.time.Duration/between (java.time.Instant/now) inst)) seconds)))


(defn http-request
  [{:keys [method headers body url json?] :as args}]
  (let [f (case method
            :post curl/post
            :get curl/get)]
    (let [{:keys [status] :as response} (f url (select-keys args [:headers :body]))]
      (cond-> response
        (and json? (<= 200 status 299))
        (update :body #(json/parse-string % true))))))


(defn api-url
  [{:keys [region]} path]
  (str "https://oidc." region ".amazonaws.com" path))


(defn get-new-client
  [client-name opts]
  (:body (http-request {:method :post
                        :url (api-url opts "/client/register")
                        :headers {"Content-type" "application/json"}
                        :json? true
                        :body (json/generate-string {"clientName" client-name
                                                     "clientType" "public"})})))


(defn get-client
  [client-name {:keys [role-name role region account-id] :as opts}]
  (let [path (str (System/getenv "HOME") "/.aws/sso/cache/" client-name ".json")
        f (java.io.File. path)
        act (fn []
              (let [client (get-new-client client-name opts)]
                (future
                  (clojure.java.io/make-parents f)
                  (spit f (json/generate-string client)))
                client))]
    (if (.exists f)
      (let [{:keys [clientSecretExpiresAt] :as creds} (json/parse-string (slurp f) true)]
        (if (expired-within? (java.time.Instant/ofEpochSecond clientSecretExpiresAt) 60)
          (act)
          creds))
      (act))))


(defn register-device
  [client {:keys [start-url] :as opts}]
  (:body (http-request {:method :post
                        :url (api-url opts "/device_authorization")
                        :headers {"Content-type" "application/json"}
                        :json? true
                        :body
                        (json/generate-string
                          (assoc (select-keys client [:clientId :clientSecret])
                                 :startUrl start-url))})))


(defn try-access-token
  [client device opts]
  (try (http-request {:method :post
                      :url (api-url opts "/token")
                      :headers {"Content-type" "application/json"}
                      :json? true
                      :body
                      (json/generate-string
                        (assoc (select-keys client [:clientId :clientSecret])
                               "deviceCode" (:deviceCode device)
                               "grantType" "urn:ietf:params:oauth:grant-type:device_code"))})
       (catch Exception e e)))


(defn complete-verification
  [device]
    ;;; Interactively open verification complete
  (let [res (clojure.java.shell/sh "open" (:verificationUriComplete device))]
    (zero? (:exit res))))


(defn error-with
  [msg]
  (throw (ex-info msg {:babashka/exit 1})))


(defn get-access-token
  [client device opts]
  (if-not (complete-verification device)
    (error-with "error complete device")
    (loop [i 0]
      (if (= i 150)
        (error-with "no response with 30 seconds")
        (do
          (Thread/sleep (case i 200))
          ; (print ".") (flush)
          (let [response (try-access-token client device opts)]
            (if (= (:status response) 200)
              (get-in response [:body :accessToken])
              (recur (inc i)))))))))


(defn get-role-credentials
  [{:keys [access-token region account-id role-name]}]
  (-> (http-request {:method :get
                     :url (format "https://portal.sso.%s.amazonaws.com/federation/credentials?account_id=%s&role_name=%s" region account-id role-name)
                     :headers {"x-amz-sso_bearer_token" access-token}
                     :json? true})
      (get-in [:body :roleCredentials])
      (clojure.set/rename-keys {:accessKeyId :AccessKeyId
                                :secretAccessKey :SecretAccessKey
                                :sessionToken :SessionToken
                                :expiration :Expiration})
      (update :Expiration (fn [ts] (str (java.time.Instant/ofEpochSecond (/ ts 1000)))))))


(defn get-sso-credentials
  [{:keys [role-name region account-id start-url] :as opts}]
  (let [client-name "aws_sso_cli_client" #_(str (java.util.UUID/randomUUID))
        client (get-client client-name opts)
        device (register-device client opts)
        access-token (get-access-token client device opts)]
    (-> (get-role-credentials {:access-token access-token
                               :region region
                               :account-id account-id
                               :role-name role-name})
        (assoc :Version 1))))
