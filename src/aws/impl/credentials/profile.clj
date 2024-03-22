;; Inlining it here, saves another 10ms
(babashka.deps/add-deps '{:deps {com.grzm/awyeah-api {:git/url "https://github.com/grzm/awyeah-api",
                                                      :git/sha "0fa7dd51f801dba615e317651efda8c597465af6"}}})


(ns aws.impl.credentials.profile
  (:require
    [babashka.process :refer [tokenize]]
    [cheshire.core :as json]
    [com.grzm.awyeah.config :as aws.config]))


(defn run-cmd-in-process
  [args]
  (with-out-str (apply (resolve 'aws.console/-main) args)))


(defn run-credential-process-cmd
  [cmd]
  (let [[binary-name & args :as cmd] (tokenize cmd)]
    (if (= binary-name "aws.console")
      (run-cmd-in-process args)

	  ;; External way of getting (nested) credentials
      (let [{:keys [exit out err] :as res} (clojure.java.shell/sh cmd)]
        (if (zero? exit)
          out
          (throw (ex-info (str "Non-zero exit: " (pr-str err)) {})))))))


(defn get-credentials-via-cmd
  [cmd]
  (let [json (run-credential-process-cmd cmd)]
    (json/parse-string json true)))


(defn get-credentials-by-profile
  [profile-name]
  (let [profiles (aws.config/parse (str (System/getenv "HOME") "/.aws/credentials"))]
    (if-let [profile (get profiles profile-name)]
      (if-let [cmd (get profile "credential_process")]
        (get-credentials-via-cmd cmd)
        (throw (ex-info (str "No credential_process defined for " (pr-str profile-name))
                        {:babashka/exit 1
                         :profile profile})))
      (throw (ex-info (str "profile " (pr-str profile-name) " doesn't exist")
                      {:babashka/exit 1
                       :profile profile-name})))))
