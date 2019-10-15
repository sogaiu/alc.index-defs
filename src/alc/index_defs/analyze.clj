(ns alc.index-defs.analyze
  (:require
   [alc.index-defs.fs :as aif]
   [alc.index-defs.paths :as aip]
   [clj-kondo.core :as cc]
   ;; patching clj-kondo -- order important
   [alc.index-defs.massage]))

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
         lint-paths
         (map (fn [path]
                (let [f (java.io.File. path)]
                  (if (not (.isAbsolute f))
                    (aif/path-join proj-root path)
                    path)))
           paths)
         results (cc/run! {:lint lint-paths
                           :config {:output {:analysis true
                                             :canonical-paths true
                                             :format :edn}}})
         duration (- (System/currentTimeMillis) start-time)]
     (when verbose
       (println (str "  duration: " duration " ms")))
     [results lint-paths])))

(defn study-project-and-deps
  ([proj-root]
   (study-project-and-deps proj-root {:verbose false}))
  ([proj-root {:keys [:method :paths :verbose] :as opts}]
   (when verbose
     (println "* determining paths to analyze"))
   (if paths
     (do
       (when verbose
         (println "  >> using passed in paths <<"))
       (analyze-paths proj-root paths opts))
     (let [shadow-file (java.io.File.
                         (aif/path-join proj-root
                           "shadow-cljs.edn"))
           shadow-exists (.exists shadow-file)
           _ (when (and verbose
                     shadow-exists)
               (println  "  found shadow-cljs.edn"))
           deps-file (java.io.File.
                       (aif/path-join proj-root
                         "deps.edn"))
           deps-exists (.exists deps-file)
           _ (when (and verbose
                     deps-exists)
               (println  "  found deps.edn"))
           project-clj-file (java.io.File.
                              (aif/path-join proj-root
                                "project.clj"))
           project-clj-exists (.exists project-clj-file)
           _ (when (and verbose
                     project-clj-exists)
               (println  "  found project.clj"))
           method (or method
                    (cond
                      shadow-exists
                      :shadow-cljs
                      ;;
                      deps-exists
                      :clj
                      ;;
                      project-clj-exists
                      :lein
                      ;;
                      :else
                      nil))]
       (assert method
         (str "No shadow-cljs.edn, deps.edn, or project.clj in: " proj-root))
       (when verbose
         (println (str "  >> classpath computation by: " (name method) " <<")))
       (when-let [path-desc (aip/get-lint-paths method
                              proj-root {:verbose verbose})]
         (analyze-paths proj-root path-desc opts))))))

(defn load-analysis
  [path {:keys [:verbose]}]
  (when verbose
    (println "* loading pre-existing analysis"))
  (-> path
    slurp
    read-string
    :analysis))

;; load-analysis is supposed to read in something like the following
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

