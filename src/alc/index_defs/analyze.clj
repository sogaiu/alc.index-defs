(ns alc.index-defs.analyze
  (:require
   [clj-kondo.core :as cc]
   [clojure.java.shell :as cjs]))

;; XXX: requires clj / clojure cli
(defn study-project-and-deps
  ([proj-root]
   (study-project-and-deps proj-root {:verbose false}))
  ([proj-root {:keys [verbose]}]
   (let [{:keys [:err :exit :out]} (cjs/with-sh-dir proj-root
                                     (cjs/sh "clj" "-Spath"))]
     ;;(println (str "err: " err))
     ;;(println (str "exit: " exit))
     ;;(println (str "out: " out))
     (assert (= 0 exit)
       (str "`clj -Spath` failed to determine classpath\n"
         "  exit\n" exit "\n"
         "  out:\n" out "\n"
         "  err:\n" err "\n"))
     (when out
       (when verbose
         (println "* analyzing project source and deps w/ clj-kondo..."))
       (let [start-time (System/currentTimeMillis)
             ;; out has a trailing newline because clj uses echo
             paths (clojure.string/split (clojure.string/trim out)
                     (re-pattern (System/getProperty "path.separator")))
             ;; some paths are relative, that can be a problem because
             ;; clj-kondo doesn't necessarily resolve them relative to
             ;; an appropriate directory
             full-paths
             (map (fn [path]
                    (let [f (java.io.File. path)]
                      (if (not (.isAbsolute f))
                        (let [pr-nio-path (java.nio.file.Paths/get proj-root
                                            (into-array String ""))
                              full-path (->> path
                                          (.resolve pr-nio-path)
                                          .toString)]
                          full-path)
                        path)))
                      paths)
             lint (cc/run! {:lint full-paths
                            :config {:output {:analysis true
                                              :format :edn
                                              :canonical-paths true}}})
             duration (- (System/currentTimeMillis) start-time)]
         (when verbose
           (println (str "  duration: " duration " ms")))
         lint)))))

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

