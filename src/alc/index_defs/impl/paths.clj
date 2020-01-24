(ns alc.index-defs.impl.paths
  (:require
   [clojure.java.io :as cji]
   [clojure.java.shell :as cjs]
   [clojure.string :as cs]))

;; windows 10 PATHEXT defautl?:
;;   .COM;.EXE;.BAT;.CMD;.VBS;.VBE;.JS;.JSE;.WSF;.WSH;.MSC
(defn which
  [bin-name]
  (let [paths (cs/split (or (System/getenv "PATH") "")
                (re-pattern (java.io.File/pathSeparator)))
        ;; for windows
        pathexts (cs/split (or (System/getenv "PATHEXT") "")
                   (re-pattern (java.io.File/pathSeparator)))]
    ;; adapted work by taylorwood
    (first
      (for [path (distinct paths)
            pathext pathexts
            :let [exe-file (cji/file path (str bin-name pathext))]
            :when (.exists exe-file)]
        (.getAbsolutePath exe-file)))))

(defmulti get-lint-paths
  (fn [method proj-root opts]
    method))

(defmethod get-lint-paths :boot
  [_ proj-root {:keys [:verbose]}]
  (let [boot-path (which "boot")
        _ (assert boot-path
            "failed to find boot")
        {:keys [:err :exit :out]}
        (cjs/with-sh-dir proj-root
          (cjs/sh boot-path "with-cp" "-w" "-f" "-"))]
    (assert (= 0 exit)
      (str "`boot with-cp -w -f` failed to determine classpath\n"
        "  exit\n" exit "\n"
        "  out:\n" out "\n"
        "  err:\n" err "\n"
        "  boot-path:\n" boot-path "\n"))
    (cs/trim out)))

;; XXX: any benefit in using tools.deps directly?
(defmethod get-lint-paths :clj
  [_ proj-root {:keys [:verbose]}]
  (let [clj-path (which "clj")
        _ (assert clj-path
            "failed to find clj")
        {:keys [:err :exit :out]}
        (cjs/with-sh-dir proj-root
          (cjs/sh clj-path "-Spath"))]
    (assert (= 0 exit)
      (str "`clj -Spath` failed to determine classpath\n"
        "  exit\n" exit "\n"
        "  out:\n" out "\n"
        "  err:\n" err "\n"
        "  clj-path:\n" clj-path "\n"))
    ;; out has a trailing newline because clj uses echo
    (cs/trim out)))

(defmethod get-lint-paths :custom
  [_ proj-root {:keys [:cp-command :verbose]}]
  (let [cmd (first cp-command)
        custom-path (which cmd)
        _ (assert custom-path
            (str "failed to find " cmd))
        {:keys [:err :exit :out]}
        (cjs/with-sh-dir proj-root
          (apply cjs/sh cp-command))]
    (assert (= 0 exit)
      (str "`"
        (cs/join " " cp-command)
        "` failed to determine classpath\n"
        "  exit\n" exit "\n"
        "  out:\n" out "\n"
        "  err:\n" err "\n"
        "  custom-path:\n" custom-path "\n"))
    (cs/trim out)))

(defmethod get-lint-paths :lein
  [_ proj-root {:keys [:verbose]}]
  (let [lein-path (which "lein")
        _ (assert lein-path
            "failed to find lein")
        {:keys [:err :exit :out]}
        (cjs/with-sh-dir proj-root
          (cjs/sh lein-path "classpath"))]
    (assert (= 0 exit)
      (str "`lein classpath` failed to determine classpath\n"
        "  exit\n" exit "\n"
        "  out:\n" out "\n"
        "  err:\n" err "\n"
        "  lein-path:\n" lein-path "\n"))
    (cs/trim out)))

;; XXX: yarn over npx -- provide way to force one?
(defmethod get-lint-paths :shadow-cljs
  [_ proj-root {:keys [:verbose]}]
  (let [yarn-lock (cji/file proj-root "yarn.lock")
        pkg-lock (cji/file proj-root "package-lock.json")
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
                      yarn?
                      "--silent"
                      ;;
                      npx?
                      "--quiet"
                      ;;
                      :else
                      (assert false
                        (str "unexpected runner: " runner)))
        _ (when verbose
            (println (str "  chose " runner " to invoke shadow-cljs")))
        runner-path (cond
                      yarn?
                      (which "yarn")
                      ;;
                      npx?
                      (which "npx")
                      ;;
                      :else
                      (assert false
                        (str "*** unexpected condition ***")))
        _ (assert runner-path
            (str "failed to find " runner))
        ;; min version of shadow-cljs 2.8.53 for classpath ability
        {:keys [:err :exit :out]}
        (cjs/with-sh-dir proj-root
          (cjs/sh runner-path silent-flag "shadow-cljs" "info"))
        [_ major minor patch]
        (re-find #"cli version: (\d+)\.(\d+)\.(\d+)" err)
        _ (assert (and (>= (Integer/parseInt major) 2)
                    (>= (Integer/parseInt minor) 8)
                    (>= (Integer/parseInt patch) 53))
                  (str "shadow-cljs version too low: "
                       major "." minor "." patch))
        {:keys [:err :exit :out]}
        (cjs/with-sh-dir proj-root
          (cjs/sh runner-path silent-flag "shadow-cljs" "classpath"))]
    (assert (= 0 exit)
      (str "`" runner " shadow-cljs classpath` "
        "failed to determine classpath\n"
        "  exit\n" exit "\n"
        "  out:\n" out "\n"
        "  err:\n" err "\n"
        "  runner-path:\n" runner-path "\n"))
    (cs/trim out)))
