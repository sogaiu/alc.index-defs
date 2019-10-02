(ns alc.index-defs.analyze
  (:require
   [alc.index-defs.fs :as aif]
   [clj-kondo.core :as cc]
   [clojure.java.shell :as cjs]))

(defmulti get-paths
  (fn [method proj-root opts]
    method))

;; XXX: yarn over npx -- provide way to force one?
(defmethod get-paths :shadow-cljs
  [_ proj-root {:keys [:verbose]}]
  (let [yarn-lock (java.io.File.
                    (aif/path-join proj-root
                      "yarn.lock"))
        pkg-lock (java.io.File.
                   (aif/path-join proj-root
                     "package-lock.json"))
        yarn? (.exists yarn-lock)
        _ (when (and verbose yarn?)
            (println (str "  found yarn.lock in: " proj-root)))
        npx? (.exists pkg-lock)
        _ (when (and verbose npx?)
            (println (str "  found package-lock.json in: " proj-root)))
        runner (cond
                 (and yarn? npx?)
                 (if (>= (.lastModified yarn-lock)
                       (.lastModified pkg-lock))
                   "yarn"
                   "npx")
                 ;;
                 yarn? "yarn"
                 ;;
                 npx? "npx"
                 ;;
                 :else
                 (assert false
                   (str "No yarn.lock or package-lock.json in: " proj-root)))
        _ (when verbose
            (println (str "  chose " runner " to invoke shadow-cljs")))
        {:keys [:err :exit :out]}
        (cjs/with-sh-dir proj-root
          (cjs/sh runner "shadow-cljs" "classpath"))]
    (assert (= 0 exit)
      (str "`" runner " shadow-cljs classpath` "
        "failed to determine classpath\n"
        "  exit\n" exit "\n"
        "  out:\n" out "\n"
        "  err:\n" err "\n"))
    (clojure.string/trim out)))

;; XXX: any benefit in using tools.deps directly?
(defmethod get-paths :clj
  [_ proj-root {:keys [:verbose]}]
  (let [{:keys [:err :exit :out]} (cjs/with-sh-dir proj-root
                                    (cjs/sh "clj" "-Spath"))]
    (assert (= 0 exit)
      (str "`clj -Spath` failed to determine classpath\n"
        "  exit\n" exit "\n"
        "  out:\n" out "\n"
        "  err:\n" err "\n"))
    ;; out has a trailing newline because clj uses echo
    (clojure.string/trim out)))

(defmethod get-paths :lein
  [_ proj-root {:keys [:verbose]}]
  (let [{:keys [:err :exit :out]} (cjs/with-sh-dir proj-root
                                    (cjs/sh "lein" "classpath"))]
    (assert (= 0 exit)
      (str "`lein classpath` failed to determine classpath\n"
        "  exit\n" exit "\n"
        "  out:\n" out "\n"
        "  err:\n" err "\n"))
    (clojure.string/trim out)))

(defn analyze-paths
  ([proj-root path-desc]
   (analyze-paths proj-root path-desc {:verbose false}))
  ([proj-root path-desc {:keys [verbose]}]
   (when verbose
     (println "* analyzing project source and deps w/ clj-kondo..."))
   (let [start-time (System/currentTimeMillis)
         paths (clojure.string/split path-desc
                 (re-pattern (System/getProperty "path.separator")))
         ;; some paths are relative, that can be a problem because
         ;; clj-kondo doesn't necessarily resolve them relative to
         ;; an appropriate directory
         full-paths
         (map (fn [path]
                (let [f (java.io.File. path)]
                  (if (not (.isAbsolute f))
                    (aif/path-join proj-root path)
                    path)))
           paths)
         lint (cc/run! {:lint full-paths
                        :config {:output {:analysis true
                                          :format :edn
                                          :canonical-paths true}}})
         duration (- (System/currentTimeMillis) start-time)]
     (when verbose
       (println (str "  duration: " duration " ms")))
     lint)))

(defn study-project-and-deps
  ([proj-root]
   (study-project-and-deps proj-root {:verbose false}))
  ([proj-root {:keys [verbose method] :as opts}]
   (let [method (or method
                  (cond
                    (.exists (java.io.File.
                               (aif/path-join proj-root
                                 "shadow-cljs.edn")))
                    :shadow-cljs
                    ;;
                    (.exists (java.io.File.
                               (aif/path-join proj-root
                                 "deps.edn")))
                    :clj
                    ;;
                    (.exists (java.io.File.
                               (aif/path-join proj-root
                                 "project.clj")))
                    :lein
                    ;;
                    :else
                    nil))]
     (assert method
       (str "No shadow-cljs.edn, deps.edn, or project.clj in: " proj-root))
     (when verbose
       (println (str "* classpath determination type: " method)))
     (when-let [path-desc (get-paths method
                            proj-root {:verbose verbose})]
       (analyze-paths proj-root path-desc opts)))))

(defn load-lint
  [path]
  (-> path
    slurp
    read-string))

;; load-lint is supposed to read in something like the following
(comment
  
  ;; structure of analysis data:
  ;;
  ;;   https://github.com/borkdude/clj-kondo/tree/
  ;;     120dd79bf3982c580e7784a41795853b44c9e4b0/analysis#user-content-data
  ;;
  {:findings [] ; not usually empty in practice
   :summary {} ; not usually empty in practice
   :analysis
   {:namespace-definitions
    [{:filename (str (System/getenv "HOME")
                  "/src/antoine/src/antoine/renderer.cljs")
      :row 1
      :col 1
      :name antoine.renderer}
     {:filename (str (System/getenv "HOME")
                  "/.m2/repository/com/wsscode/pathom/2.2.7/pathom-2.2.7.jar"
                  ":"
                  "com/wsscode/pathom/parser.cljc")
      :row 1
      :col 1
      :name com.wsscode.pathom.parser
      :lang :clj}]
    :namespace-usages
    [{:filename (str (System/getenv "HOME")
                  "/src/antoine/src/antoine/renderer.cljs")
      :row 3
      :col 5
      :from antoine.renderer
      :to clojure.core.async}]
    :var-definitions
    [{:filename (str (System/getenv "HOME")
                  "/.m2/repository/com/wsscode/pathom/2.2.7/pathom-2.2.7.jar"
                  ":"
                  "com/wsscode/pathom/parser.cljc")
      :row 13
      :col 1
      :ns com.wsscode.pathom.parser
      :name expr->ast
      :lang :clj}]
    :var-usages
    [{:name defmacro
      :var-args-min-arity 2
      :lang :cljs
      :filename (str (System/getenv "HOME")
                  "/.m2/repository/org/clojure/clojurescript/1.10.520/"
                  "clojurescript-1.10.520.jar"
                  ":"
                  "cljs/spec/gen/alpha.cljc")
      :from cljs.spec.gen.alpha
      :macro true
      :col 1
      :arity 4
      :row 62
      :to cljs.core}]}}
              
  )

