(ns alc.index-defs.impl.analyze
  (:require
   [alc.index-defs.impl.fs :as aiif]
   [alc.index-defs.impl.paths :as aiip]
   [clj-kondo.core :as cc]
   [clojure.string :as cs]))

(defn analyze-paths
  ([proj-root path-desc]
   (analyze-paths proj-root path-desc {:verbose false}))
  ([proj-root path-desc {:keys [verbose]}]
   (when verbose
     (println "* analyzing project source and deps w/ clj-kondo..."))
   (let [start-time (System/currentTimeMillis)
         paths (cs/split path-desc
                 (re-pattern (System/getProperty "path.separator")))
         ;; some paths are relative, that can be a problem because
         ;; clj-kondo doesn't necessarily resolve them relative to
         ;; an appropriate directory
         lint-paths
         (map (fn [path]
                (let [f (java.io.File. path)]
                  (if (not (.isAbsolute f))
                    (aiif/path-join proj-root path)
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
  ([proj-root {:keys [:cp-command :method :paths :verbose] :as opts}]
   (when verbose
     (println "* determining paths to analyze"))
   (cond
     paths
     (do
       (when verbose
         (println "  >> using passed in paths <<"))
       (analyze-paths proj-root paths opts))
     ;;
     cp-command
     (do
       (when verbose
         (println (str "  >> classpath computation by: "
                    (cs/join " " cp-command)
                    " <<")))
       (let [path-desc (aiip/get-lint-paths :custom
                         proj-root {:cp-command cp-command
                                    :verbose verbose})]
         (assert (not= path-desc "")
           "No paths to analyze")
         (analyze-paths proj-root path-desc opts)))
     ;; possibly method is supplied
     :else
     (let [shadow-file (java.io.File.
                         (aiif/path-join proj-root
                           "shadow-cljs.edn"))
           shadow-exists (.exists shadow-file)
           _ (when (and verbose
                     shadow-exists)
               (println  "  found shadow-cljs.edn"))
           deps-file (java.io.File.
                       (aiif/path-join proj-root
                         "deps.edn"))
           deps-exists (.exists deps-file)
           _ (when (and verbose
                     deps-exists)
               (println  "  found deps.edn"))
           project-clj-file (java.io.File.
                              (aiif/path-join proj-root
                                "project.clj"))
           project-clj-exists (.exists project-clj-file)
           _ (when (and verbose
                     project-clj-exists)
               (println  "  found project.clj"))
           build-boot-file (java.io.File.
                             (aiif/path-join proj-root
                               "build.boot"))
           build-boot-exists (.exists build-boot-file)
           _ (when (and verbose
                     build-boot-exists)
               (println  "  found build.boot"))
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
                      build-boot-exists
                      :boot
                      ;;
                      :else
                      nil))]
       (assert method
         (str "No shadow-cljs.edn, deps.edn, project.clj, or build.boot in: "
           proj-root))
       (when verbose
         (println (str "  >> classpath computation by: " (name method) " <<")))
       (let [path-desc (aiip/get-lint-paths method
                         proj-root {:verbose verbose})]
         (assert (not= path-desc "")
           "No paths to analyze")
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
  ;;   :lang: if occurred in a .cljc file, the language in which it was
  ;;   resolved: :clj or :cljs
  ;;
  {:findings [] ; not usually empty in practice
   :summary {} ; not usually empty in practice
   :analysis
   {:namespace-definitions
    ;; sometimes there's :lang, but only if in .cljc file
    [{:filename (str (System/getenv "HOME")
                  "/src/antoine/src/antoine/renderer.cljs")
      :row 1
      :col 1
      :name 'antoine.renderer}
     {:filename (str (System/getenv "HOME")
                  "/.m2/repository/com/wsscode/pathom/2.2.7/pathom-2.2.7.jar"
                  ":"
                  "com/wsscode/pathom/parser.cljc")
      :row 1
      :col 1
      :name 'com.wsscode.pathom.parser
      :lang :clj}]
    :namespace-usages
    ;; sometimes there's :lang, but only if in .cljc file
    ;; sometimes there's :alias, but it is extra info about the
    ;; namespace - the entry is not just about the alias
    [{:filename (str (System/getenv "HOME")
                  "/src/antoine/src/antoine/renderer.cljs")
      :row 3 ; row of :to (e.g. of in [clojure.core.async] in require)
      :col 5 ; col of :to (e.g. of in [clojure.core.async] in require)
      :from 'antoine.renderer
      :to 'clojure.core.async}]
    :var-definitions
    ;; sometimes there's :lang, but only if in .cljc file
    [{:filename (str (System/getenv "HOME")
                  "/.m2/repository/com/wsscode/pathom/2.2.7/pathom-2.2.7.jar"
                  ":"
                  "com/wsscode/pathom/parser.cljc")
      :row 13
      :col 1
      :ns 'com.wsscode.pathom.parser
      :name 'expr->ast
      :lang :clj}]
    :var-usages
    ;; sometimes there's :lang, but only if in .cljc file
    [{:name defmacro
      :var-args-min-arity 2 ; optional
      :lang :cljs  ; optional
      :filename (str (System/getenv "HOME")
                  "/.m2/repository/org/clojure/clojurescript/1.10.520/"
                  "clojurescript-1.10.520.jar"
                  ":"
                  "cljs/spec/gen/alpha.cljc")
      :from 'cljs.spec.gen.alpha
      :macro true ; optional
      :col 1
      :arity 4 ; optional
      :row 62
      :to 'cljs.core}]}} ; can be empty (was for :name js/isNaN, js/parseInt)

  )
