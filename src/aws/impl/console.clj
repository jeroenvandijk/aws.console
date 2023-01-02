(ns aws.impl.console
  (:require
    [cheshire.core :as json]))


(def aws-auth-url "https://eu-central-1.signin.aws.amazon.com/federation")

(import '[java.net URLEncoder])


(defn url-encode
  [s]
  (URLEncoder/encode s "UTF-8"))

;; Issuer parameter is optional. Needed when the users needs to be redirected when the session ends (See The issuer parameter is optional)
(defn console-get-signin-token
  [creds]
  (let [creds         {"sessionId"    (get creds :AccessKeyId)
                       "sessionKey"   (get creds :SecretAccessKey)
                       "sessionToken" (get creds :SessionToken)}
        encoded-creds (url-encode (json/generate-string creds))
        url           (str aws-auth-url
                           "?Action=getSigninToken&SessionDuration=1800&Session="
                           encoded-creds)]
    (-> (babashka.curl/get url)
        (get :body)
        (json/parse-string true)
        (get :SigninToken))))


(defn aws-console-url
  [{:keys [creds path url]}]
  (let [signin-token (console-get-signin-token creds)
         ;; REVIEW options to open console directly where we want
         ;; path "/cloudformation/home?region=us-east-1"
        destination (or url (str "https://console.aws.amazon.com" path))]
    (str aws-auth-url
         "?Action=login&Destination="
         (url-encode destination)
         "&SigninToken="
         signin-token)))


(defn open-application
  [app-name & args]
  (let [cmd (cond-> ["open" "-na" app-name]
              args
              (-> (conj "--args")
                  (into args)))]
    (apply clojure.java.shell/sh cmd)))


(def browsers
  {:brave {:app "Brave browser"
           :incognito "--incognito"}

   :chrome {:app "Google chrome"
            :incognito "--incognito"}

   ;; See https://www.cyberciti.biz/faq/howto-run-firefox-from-the-command-line/
   :firefox {:app "Firefox"
             :incognito "--private-window"}})


  ;; Review other browsers?
(defn open-browser
  [url {:keys [incognito browser]}]
  (let [browser (or browser :chrome)
        incognito (or incognito false)

        {:keys [app] incognito-opt :incognito} (get browsers browser)]
    ;(open-application app [url])
    (if app
      (apply open-application app (if incognito [incognito-opt url] [url]))
      (clojure.java.browse/browse-url url))))
