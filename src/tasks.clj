(ns tasks)


(defn path->ns
  [path]
  (->
    path
    (clojure.string/replace ".clj" "")
    (clojure.string/split #"/")
    (rest)
    (->>
      (clojure.string/join "."))))


(defn generate-inline-script
  [& m]
  (spit "target/inline.clj"
        (with-out-str
          (do
            (println "#!/usr/bin/env bb")
            (println)
            #_(prn (list 'babashka.deps/add-deps (list 'quote (clojure.edn/read-string (slurp "deps.edn")))))

            (println)

            (println "(def namespaces { ")

            ;; FIXME Below is specific for this project
            (doseq [f (file-seq (clojure.java.io/file "src/aws/impl"))]
              (when (.isFile f)
                (println (pr-str (path->ns (.getPath f)))  " (delay (load-string ")
                (println (pr-str (slurp f)))

                ;; REVIEW can we remove comments and make loading even faster?
                #_(println (pr-str (pr-str
                                    (clojure.edn/read-string (str "" (slurp f) ""))

                                    #_(clojure.edn/read-string (str "'(do " (slurp f) ")")))))
                (println  "))")))

            (println "})")

            (prn '(defn dynamic-requiring-resolve
                    [n]
                    (if-let [*n (get namespaces (namespace n))]
                      (do
                        (deref *n)
                        (requiring-resolve n))
                      (throw (ex-info "Could not find namespace" {:n n})))))

            (println (-> (slurp (clojure.java.io/file "src/aws/console.clj"))
                         (clojure.string/replace "requiring-resolve" "user/dynamic-requiring-resolve"))))))
  m)
