(ns aws.impl.credentials.saved
  (:require
    [cheshire.core :as json])
  (:import
    (java.time
      Instant)))


(def credentials-root-dir (str (System/getenv "HOME") "/.aws/console"))


(defn expired?*
  [creds]
  (if-some [token-ts (some-> creds :Expiration (Instant/parse))]
    (.isBefore ^Instant token-ts ^Instant (Instant/now))
    true))


(defn credentials-file-path
  [account-id role-name]
  (let [path (str account-id "/" role-name)
        credentials-dir (str credentials-root-dir "/credentials/" path)]
    (str credentials-dir "/creds.json")))


(defn read-creds-from-file*
  [credentials-file]
  (when (.exists ^File credentials-file)
    (json/parse-string (slurp credentials-file) true)))


(defn saved-credentials
  [account role]
  (let [f (clojure.java.io/file (credentials-file-path account role))
        creds (read-creds-from-file* f)]
    (when (and
            creds
            (not (expired?* creds)))
      creds)))


(defn save-credentials!
  [creds account role]
  (let [f (clojure.java.io/file (credentials-file-path account role))]
    (clojure.java.io/make-parents f)
    (spit f (json/generate-string creds))))


(defn with-saved-credentials
  [{:keys [account role]} thunk]
  (or (saved-credentials account role)
      (let [creds (thunk)]
        (save-credentials! creds account role)
        creds)))
