(ns aws.console
  (:require
    [babashka.cli :as cli]
    [cheshire.core :as json]))


(defn creds-by-profile
  [profile]
  ((requiring-resolve 'aws.impl.credentials.profile/get-credentials-by-profile) profile))


(defn with-saved-credentials
  [opts thunk]
  (let [f (requiring-resolve 'aws.impl.credentials.saved/with-saved-credentials)]
    (f opts thunk)))

;; TODO Add config

;; https://gist.github.com/garnaat/10682964#file-gistfile1-py-L44

(defn console
  [{print-creds? :print-creds
    print-url? :print-url
    debug? :debug
    force? :force
    :keys [profile
           incognito
           sso-url

           sts account role via session-name region] :as opts}]
  (let [creds (cond (.ready *in*)
                    (-> (slurp *in*)
                        (json/parse-string true))

                    sso-url
                    (do
                      (assert (and account role))
                      (with-saved-credentials {:account account
                                               :role role
                                               :force? force?}
                        (fn []
                          (let [sso-credentials (requiring-resolve 'aws.impl.credentials.sso/get-sso-credentials)]
                            (sso-credentials {:account-id account :role-name role :region region :start-url sso-url})))))

                    sts
                    (do
                      (assert (and account role sts))
                      (with-saved-credentials
                        {:account account
                         :role role
                         :force? force?}
                        (fn []
                          (when debug?
                            (println (str "No credentials found, retrieving via profile " sts)))
                          (let [creds (creds-by-profile sts)
                                assume-role (requiring-resolve 'aws.impl.credentials.sts/assume-role)]
                            (assume-role {:creds creds
                                          :account-id account
                                          :role-name role
                                          :session-name (or session-name "session")
                                         ;; https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/using-regions-availability-zones.html
                                          :region (or region "eu-central-1")})))))
                    ;; --region sa-east-1 is much slower

                    ;; TODO keep cache per profile? With force this would be removed
                    profile
                    (creds-by-profile profile)

                    :else
                    (throw (ex-info "Missing credentials" {:babashka/exit 1})))]

    (if print-creds?
      (println (json/generate-string creds))
      (let [aws-console-url (requiring-resolve 'aws.impl.console/aws-console-url)
            url (aws-console-url (assoc opts :creds creds))]
        (if print-url?
          (println url)
          (let [open-browser (requiring-resolve 'aws.impl.console/open-browser)]
            (when debug?
              (println "Opening browser"))
            (open-browser url opts)))))))


(defn -main
  [& args]
  (console (cli/parse-opts args {:coerce {:profile :string

                                          :region :string
                                          :path :string
                                          :url :string

                                          :print-url :boolean
                                          :print-creds :boolean

                                          :sts :string
                                          :account :string
                                          :role :string

                                          ;; Pattern [\\w+=,.@-]*\n31:
                                          :session-name :string

                                          :debug :boolean
                                          :force :boolean

                                          :browser :keyword
                                          :incognito :boolean}}))
  nil)


(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))


;; Try bb aws.console.clj --profile production --path "/athena/home?region=ap-southeast-1"
;;     bb aws.console.clj --profile production --path "/iam/home?region=us-east-2"
;;     bb aws.console.clj --profile production --url https://ap-southeast-1.console.aws.amazon.com/athena/home\?region\=ap-southeast-1\#/query-editor
;;     Can we make it like `aws.console https://ap-southeast-1.console.aws.amazon.com/athena/home\?region\=ap-southeast-1\#/query-editor --profile production or should we append --optoins

;; Wanna try to extract


#_ (deftask console
    "Logs you into the AWS console using credentials"
    [_ env ENV edn "Config map"
     _ profile PROFILE_NAME str "Profile name to use from ~/.aws/credentials"
     _ account ACCOUNT_ID str "Account id"
     _ role ROLE_NAME str "Role name"
     _ via VIA edn "edn vector of {:account-id ... :role-name ... :force? }"
     _ force bool "Removes old credentials and re-fetches credentials"
     _ session-name SESSION_NAME nil str "Name of session that endsup in console"
     _ incognito false bool "Use incognito mode"]
    (let [creds (cli/aws-credentials (prep-args))]
      (if incognito
        (cli/aws-console-incognito creds)
        (cli/aws-console creds))))
