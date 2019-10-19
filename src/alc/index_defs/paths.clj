(ns alc.index-defs.paths
  (:require
   [alc.index-defs.fs :as aif]
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
            (println "  found yarn.lock"))
        npx? (.exists pkg-lock)
        _ (when (and verbose npx?)
            (println "  found package-lock.json"))
        runner (cond
                 (and yarn? npx?)
                 (if (>= (.lastModified yarn-lock)
                       (.lastModified pkg-lock))
                   (do (when verbose
                         (println "  >> yarn.lock newer <<"))
                       "yarn")
                   (do (when verbose
                         (println "  >> package-lock.json newer <<"))
                       "npx"))
                 ;;
                 yarn? "yarn"
                 ;;
                 npx? "npx"
                 ;;
                 :else
                 (assert false
                   (str "No yarn.lock or package-lock.json in: " proj-root)))
        silent-flag (cond
                      (= runner "yarn")
                      "--silent"
                      ;;
                      (= runner "npx")
                      "--quiet"
                      ;;
                      :else
                      (assert false
                        (str "unexpected runner: " runner)))
        _ (when verbose
            (println (str "  chose " runner " to invoke shadow-cljs")))
        {:keys [:err :exit :out]}
        (cjs/with-sh-dir proj-root
          (cjs/sh runner silent-flag "shadow-cljs" "classpath"))]
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
