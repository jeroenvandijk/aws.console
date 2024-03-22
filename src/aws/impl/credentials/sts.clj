;; Inlining it here, saves another 10ms
(babashka.deps/add-deps '{:deps {org.babashka/spec.alpha {:git/url "https://github.com/babashka/spec.alpha",
                                                          :git/sha "8df0712896f596680da7a32ae44bb000b7e45e68"},
                                 com.grzm/awyeah-api {:git/url "https://github.com/grzm/awyeah-api",
                                                      :git/sha "0fa7dd51f801dba615e317651efda8c597465af6"},
                                 com.cognitect.aws/endpoints {:mvn/version "1.1.12.307"},
                                 com.cognitect.aws/sts {:mvn/version "822.2.1145.0"}}})


(ns aws.impl.credentials.sts
  (:require
    [com.grzm.awyeah.client.api :as aws]
    [com.grzm.awyeah.credentials :as credentials :refer [CredentialsProvider]]))

 ;; https://github.com/grzm/awyeah-api/blob/main/dev/user.clj

(defn role-arn
  [{:keys [account-id role-name]}]
  (format "arn:aws:iam::%s:role/%s" account-id role-name))


(defn assume-role
  [{:keys [creds account-id role-name session-name region]}]
  (let [sts (aws/client {:api :sts
                         ;; Does this do something? E.g. --region eu-central-1 --print-creds --force VS --region us-east-1 --print-creds --force
                         :region (or region "us-east-1")
                         :credentials-provider
                         (reify CredentialsProvider
                           (fetch
                             [_]
                             {:aws/access-key-id (:AccessKeyId creds)
                              :aws/secret-access-key (:SecretAccessKey creds)
                              :aws/session-token (:SessionToken creds)}))})
        {:keys [ErrorResponse Credentials]}
        (aws/invoke sts
                    {:op      :AssumeRole
                     :request {:RoleArn         (role-arn {:account-id account-id
                                                           :role-name role-name})
                               :RoleSessionName session-name}})]
    (or (-> Credentials
            ;; This needs to be added for aws cli tool, move to higher level?
            (assoc :Version 1))
        ;; REVIEW Is this error message enough?
        (throw (ex-info (get-in ErrorResponse [:Error :Message])
                        ErrorResponse)))))


(comment

  (require 'aws.impl.credentials.profile)

  (time (assume-role {:creds (aws.impl.credentials.profile/get-credentials-by-profile "root-admin")
                      :account-id 722182283718
                      :role-name "OrganizationAccountAccessRole"
                      :session-name "production"
                      :region "us-east-1"
                                        ;:region "eu-west-1"
                      })))
