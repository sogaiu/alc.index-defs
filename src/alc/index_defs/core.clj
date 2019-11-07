(ns alc.index-defs.core
  (:require
   [alc.index-defs.analyze :as aia]
   [alc.index-defs.fs :as aif]
   [alc.index-defs.lookup :as ail]
   [alc.index-defs.opts :as aio]
   [alc.index-defs.tags :as ait]
   [alc.index-defs.unzip :as aiu]))

;; XXX: creating one TAGS file for the project source and 
;;      possibly one for all dependencies (or one for each dep)
;;      along with the "include" directive might be intereseting
(defn do-it!
  [opts]
  (let [{:keys [:analysis-path :cp-command :format :method :out-name
                :overwrite :paths :proj-dir :verbose] :as checked-opts}
        (aio/check opts)
        table-path (aif/path-join proj-dir out-name)
        tags-file (java.io.File. table-path)]
    ;; XXX: delete later -- as late as possible?
    (if (not overwrite)
      (assert (not (.exists tags-file))
        (str "TAGS already exists for: " proj-dir))
      (when (.exists tags-file)
        (let [result (.delete tags-file)]
          (assert result
            (str "failed to remove TAGS file for: " proj-dir)))))
    (aif/reset-cache!)
    (let [ctx {:cache aif/cache
               :checked-opts checked-opts
               :format format
               :opts opts
               :proj-dir proj-dir
               :table-path table-path
               :times [[:start-time (System/currentTimeMillis)]]}
          ctx (if analysis-path
                (let [analysis (aia/load-analysis analysis-path checked-opts)]
                  ;; XXX: lint-paths unavailable
                  (assoc ctx
                    :analysis analysis))
                ;; cp-command, method, paths, or none
                (let [[results lint-paths]
                      (aia/study-project-and-deps proj-dir checked-opts)]
                  (assert results
                    (str "analysis failed"))
                  (assoc ctx
                    :analysis (:analysis results)
                    :lint-paths lint-paths)))
          ctx (assoc ctx
                :unzip-root (aif/path-join
                              (aif/path-join proj-dir ".alc-id")
                              "unzip"))
          ;; ensure unzip-root dir exists
          _ (assert (aif/ensure-dir
                      (java.io.File. (:unzip-root ctx)))
              (str "failed to create unzip-root: " (:unzip-root ctx)))
          ctx (assoc ctx
                :ns-defs (ail/add-paths-to-ns-defs ctx))
          ctx (assoc ctx
                :var-defs (ail/add-paths-to-var-defs ctx))
          ctx (assoc ctx
                :var-uses (ail/add-paths-to-var-uses ctx))
          ;; unzip all jars
          _ (when verbose
              (println (str "* unzipping jars...")))
          ;; all distinct jar paths
          _ (aiu/unzip-jars ctx)
          _ (when verbose
              (println (str "* massaging analysis data...")))
          ;; visit-path to ns-name table
          _ (when verbose
              (println (str "  making path to ns-name table")))
          ctx (assoc ctx
                :path-to-ns-table (ail/make-path-to-ns-name-table ctx))
          ;; enhance usages info by determining full identifier names
          _ (when verbose
              (println (str "  adding full names to var uses")))
          ctx (assoc ctx
                :var-uses (doall
                            (ail/add-full-names-to-var-uses ctx)))
          ;; ns file path to var to full-name table -- takes a while
          _ (when verbose
              (println (str "  making ns-path to vars table")))
          ctx (assoc ctx
                :aka-table (ail/make-ns-path-to-vars-table ctx))
          ;; collect def entries by the file they live in
          _ (when verbose
              (println (str "  making path to defs table")))
          ctx (assoc ctx
                :visit-path-to-defs-table (ail/make-path-to-defs-table ctx))]
      ;; for each file with def entries, prepare a section and write it out
      (when verbose
        (println (str "* assembling and writing " out-name " file...")))
      ;; using clj-kondo's order is close to classpath order --
      ;; seems to have a few benefits doing it this way
      (cond
        (= format :etags)
        (ait/create-etags ctx)
        ;;
        (= format :ctags)
        (ait/create-ctags ctx)
        ;;
        :else ; should not happen
        (throw (Exception.
                 (str "Unrecognized format: " format))))
      (let [duration (- (System/currentTimeMillis) (-> (:times ctx)
                                                     (nth 0)
                                                     (nth 1)))]
        (when verbose
          ;; (println (str "  duration: "
          ;;            (- (System/currentTimeMillis) post-massaging-time)
          ;;            " ms"))
          (println (str "-------------------------"))
          (println (str "total duration: " duration " ms")))))))

(comment

  (do-it! {:overwrite true
           :proj-dir (aif/path-join (System/getenv "HOME")
                       "src/adorn")})

  ;; XXX: should error
  (do-it! {:overwrite true
           :method :shadow-cljs
           :proj-dir (aif/path-join (System/getenv "HOME")
                       "src/adorn")})

  ;; just one file
  (do-it! {:overwrite true
           :paths "src/script.clj"
           :proj-dir (aif/path-join (System/getenv "HOME")
                       "src/adorn")})

  (do-it! {:proj-dir (aif/path-join (System/getenv "HOME")
                       "src/alc.index-defs")})

  (do-it! {:proj-dir (aif/path-join (System/getenv "HOME")
                       "src/alc.index-defs")
           :verbose false})

  (do-it! {:overwrite true
           :proj-dir (aif/path-join (System/getenv "HOME")
                       "src/alc.index-defs")})

  (do-it! {:format :ctags
           :overwrite true
           :proj-dir (aif/path-join (System/getenv "HOME")
                       "src/alc.index-defs")})

  (do-it! {:format :ctags
           :out-name ".tags"
           :overwrite true
           :proj-dir (aif/path-join (System/getenv "HOME")
                       "src/alc.index-defs")})

  (do-it! {:overwrite true
           :proj-dir (aif/path-join (System/getenv "HOME")
                       "src/alens")})

  (do-it! {:proj-dir (aif/path-join (System/getenv "HOME")
                       "src/antoine")
           :verbose true})

  (do-it! {:cp-command ["yarn" "shadow-cljs" "classpath"]
           :overwrite true
           :proj-dir (aif/path-join (System/getenv "HOME")
                       "src/antoine")
           :verbose true})

  (do-it! {:method :clj
           :overwrite true
           :proj-dir (aif/path-join (System/getenv "HOME")
                       "src/antoine")})

  (do-it! {:format :ctags
           :overwrite true
           :proj-dir (aif/path-join (System/getenv "HOME")
                       "src/antoine")})

  (do-it! {:overwrite true
           :proj-dir (aif/path-join (System/getenv "HOME")
                       "src/antoine")})

  (let [m2-repos-path (aif/path-join (System/getenv "HOME")
                        ".m2/repository")
        ;; XXX: order tried first
        ;; jar-paths ["com/wsscode/pathom/2.2.7/pathom-2.2.7.jar"
        ;;            "thheller/shadow-client/1.3.2/shadow-client-1.3.2.jar"
        ;;            "thheller/shadow-cljs/2.8.55/shadow-cljs-2.8.55.jar"
        ;;            "org/clojure/core.async/0.4.500/core.async-0.4.500.jar"]
        ;; XXX: order in TAGS differed, but file size the same
        jar-paths ["com/wsscode/pathom/2.2.7/pathom-2.2.7.jar"
                   "thheller/shadow-cljs/2.8.55/shadow-cljs-2.8.55.jar"
                   "thheller/shadow-client/1.3.2/shadow-client-1.3.2.jar"
                   "org/clojure/core.async/0.4.500/core.async-0.4.500.jar"]
        lint-paths (clojure.string/join ":"
                     (concat ["src"] (map (fn [jar-path]
                                            (aif/path-join m2-repos-path
                                              jar-path))
                                       jar-paths)))]
    (println "lint-paths:" lint-paths)
    (do-it! {:overwrite true
             :paths lint-paths
             :proj-dir (aif/path-join (System/getenv "HOME")
                         "src/antoine")}))

  ;; XXX: shadow-cljs version must be >= 2.8.53
  (do-it! {:overwrite true
           :proj-dir (aif/path-join (System/getenv "HOME")
                       "src/atom-chlorine")})

  (do-it! {:overwrite true
           :proj-dir (aif/path-join (System/getenv "HOME")
                       "src/augistints")})

  (do-it! {:overwrite true
           :proj-dir (aif/path-join (System/getenv "HOME")
                       "src/babashka")})

  (do-it! {:overwrite true
           :proj-dir (aif/path-join (System/getenv "HOME")
                       "src/badigeon")})

  (do-it! {:overwrite true
           :proj-dir (aif/path-join (System/getenv "HOME")
                       "src/clj-kondo")})

  (do-it! {:method :lein
           :overwrite true
           :proj-dir (aif/path-join (System/getenv "HOME")
                       "src/clj-kondo")})

  (do-it! {:overwrite true
           :proj-dir (aif/path-join (System/getenv "HOME")
                       "src/cljfmt/cljfmt")})

  (do-it! {:overwrite true
           :paths "src/clj/clojure"
           :proj-dir (aif/path-join (System/getenv "HOME")
                       "src/clojure")})

  ;; XXX: cannot process clojure clr yet?
  (do-it! {:overwrite true
           :paths "Clojure/Clojure.Source/clojure"
           :proj-dir (aif/path-join (System/getenv "HOME")
                       "src/clojure-clr")})

  (do-it! {:overwrite true
           :proj-dir (aif/path-join (System/getenv "HOME")
                       "src/clojurescript")})

  (do-it! {:overwrite true
           :proj-dir (aif/path-join (System/getenv "HOME")
                       "src/compliment")})

  (do-it! {:overwrite true
           :proj-dir (aif/path-join (System/getenv "HOME")
                       "src/conch")})

  (do-it! {:overwrite true
           :proj-dir (aif/path-join (System/getenv "HOME")
                       "src/conjure")})

  (do-it! {:overwrite true
           :proj-dir (aif/path-join (System/getenv "HOME")
                       "src/core.async")})

  ;; project.clj appears broken atm
  (do-it! {:overwrite true
           :proj-dir (aif/path-join (System/getenv "HOME")
                       "src/core.logic")})

  (do-it! {:proj-dir (aif/path-join (System/getenv "HOME")
                       "src/debug-repl")})

  (do-it! {:overwrite true
           :proj-dir (aif/path-join (System/getenv "HOME")
                       "src/edamame")})

  ;; XXX: SNAPSHOT dep in deps.edn causing problems
  (do-it! {:overwrite true
           :proj-dir (aif/path-join (System/getenv "HOME")
                       "src/figwheel-main")})

  (do-it! {:overwrite true
           :proj-dir (aif/path-join (System/getenv "HOME")
                       "src/fs")})

  (do-it! {:overwrite true
           :proj-dir (aif/path-join (System/getenv "HOME")
                       "src/jet")})

  (do-it! {:overwrite true
           :proj-dir (aif/path-join (System/getenv "HOME")
                       "src/liquid")})

  ;; uses boot
  (do-it! {:overwrite true
           :proj-dir (aif/path-join (System/getenv "HOME")
                       "src/lumo")})

  (do-it! {:overwrite true
           :proj-dir (aif/path-join (System/getenv "HOME")
                       "src/punk")})

  (do-it! {:overwrite true
           :proj-dir (aif/path-join (System/getenv "HOME")
                       "src/reagent")})

  (do-it! {:overwrite true
           :proj-dir (aif/path-join (System/getenv "HOME")
                       "src/re-frame")})

  (do-it! {:overwrite true
           :proj-dir (aif/path-join (System/getenv "HOME")
                       "src/repl-tooling")})

  ;; XXX: potemkin makes things hard?
  (do-it! {:overwrite true
           :proj-dir (aif/path-join (System/getenv "HOME")
                       "src/rewrite-clj")})

  (do-it! {:overwrite true
           :proj-dir (aif/path-join (System/getenv "HOME")
                       "src/replique")})

  ;; has shadow-cljs, but should not use that for indexing
  (do-it! {:method :clj
           :overwrite true
           :proj-dir (aif/path-join (System/getenv "HOME")
                       "src/shadow-cljs")})

  ;; has shadow-cljs, but should not use that for indexing
  (do-it! {:method :clj
           :overwrite true
           :proj-dir (aif/path-join (System/getenv "HOME")
                       "src/sci")})

  (do-it! {:overwrite true
           :proj-dir (aif/path-join (System/getenv "HOME")
                       "src/specter")})

  (do-it! {:overwrite true
           :proj-dir (aif/path-join (System/getenv "HOME")
                       "src/tools.deps.alpha")})

  (do-it! {:overwrite true
           :proj-dir (aif/path-join (System/getenv "HOME")
                       "src/zprint")})

  )

;; XXX: may no longer work because clj-kondo doesn't retain full-fn-name
;;      but may be revivable once full names are extracted from files
;;
;; the manual way
;;
;; sample clj-kondo lint data can be produced like:
;;
;;   clj-kondo \
;;     --lint `clj -Spath` \
;;     --config '{:output {:analysis true :format :edn :canonical-paths true}}' \
;;   > clj-kondo-analysis-full-paths.edn
;;
;; see script/make-analysis.sh
;;
;; XXX: running do-it! with an out-of-date lint data file may cause
;;      problems.  e.g. the current code doesn't try to guard against if
;;      files are shorter or out-of-sync with the analysis.  this could
;;      be made more robust -- perhaps warnings should be emitted at least.
(comment

  (let [proj-dir (aif/path-join (System/getenv "HOME")
                   "src/adorn")]
    (do-it! {:analysis-path
             (aif/path-join proj-dir
               "clj-kondo-analysis-full-paths.edn")
             :overwrite true
             :proj-dir proj-dir}))

  (let [proj-dir (aif/path-join (System/getenv "HOME")
                   "src/alc.index-defs")]
    (do-it! {:analysis-path
             (aif/path-join proj-dir
               "clj-kondo-analysis-full-paths.edn")
             :proj-dir proj-dir}))

  (let [proj-dir (aif/path-join (System/getenv "HOME")
                   "src/antoine")]
    (do-it! {:analysis-path
             (aif/path-join proj-dir
               "clj-kondo-analysis-full-paths-2.edn")
             :proj-dir proj-dir}))

  )
