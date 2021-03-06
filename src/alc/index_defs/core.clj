(ns alc.index-defs.core
  (:require
   [alc.index-defs.impl.analyze :as aiia]
   [alc.index-defs.impl.ctags :as aiic]
   [alc.index-defs.impl.etags :as aiie]
   [alc.index-defs.impl.fs :as aiif]
   [alc.index-defs.impl.lookup :as aiil]
   [alc.index-defs.impl.opts :as aiio]
   [alc.index-defs.impl.unzip :as aiiu]
   [clojure.java.io :as cji]))

;; XXX: creating one TAGS file for the project source and 
;;      possibly one for all dependencies (or one for each dep)
;;      along with the "include" directive might be intereseting
(defn do-it!
  [opts]
  (let [{:keys [:analysis-path :cp-command :format :method :out-name
                :overwrite :paths :proj-dir :verbose] :as checked-opts}
        (aiio/check opts)
        table-path (.getPath (cji/file proj-dir out-name))
        tags-file (java.io.File. table-path)]
    ;; XXX: delete later -- as late as possible?
    (if (not overwrite)
      (assert (not (.exists tags-file))
        (str out-name " already exists for: " proj-dir))
      (when (.exists tags-file)
        (let [result (.delete tags-file)]
          (assert result
            (str "failed to remove TAGS file for: " proj-dir)))))
    (aiif/reset-cache!)
    (let [start-time (System/currentTimeMillis)
          ctx {:cache aiif/cache
               :checked-opts checked-opts
               :format format
               :opts opts
               :proj-dir proj-dir
               :table-path table-path
               :times [[:start-time start-time]]}
          ctx (if analysis-path
                (let [analysis (aiia/load-analysis analysis-path checked-opts)]
                  ;; XXX: lint-paths unavailable
                  (assoc ctx
                    :analysis analysis))
                ;; cp-command, method, paths, or none
                (let [[results lint-paths]
                      (aiia/study-project-and-deps proj-dir checked-opts)]
                  (assert results
                    (str "analysis failed"))
                  (assoc ctx
                    :analysis (:analysis results)
                    :lint-paths lint-paths)))
          ctx (assoc ctx
                :unzip-root (.getPath (cji/file proj-dir ".alc-id" "unzip")))
          ;; ensure unzip-root dir exists
          _ (assert (aiif/ensure-dir
                      (java.io.File. (:unzip-root ctx)))
              (str "failed to create unzip-root: " (:unzip-root ctx)))
          ctx (assoc ctx
                :ns-defs (aiil/add-paths-to-ns-defs ctx))
          ctx (assoc ctx
                :var-defs (aiil/add-paths-to-var-defs ctx))
          ctx (assoc ctx
                :var-uses (aiil/add-paths-to-var-uses ctx))
          ;; unzip all jars
          _ (when verbose
              (println (str "* unzipping jars...")))
          ;; all distinct jar paths
          _ (aiiu/unzip-jars ctx)
          _ (when verbose
              (println (str "* massaging analysis data...")))
          ;; visit-path to ns-name table
          _ (when verbose
              (println (str "  making path to ns-name table")))
          ctx (assoc ctx
                :path-to-ns-table (aiil/make-path-to-ns-name-table ctx))
          ;; enhance usages info by determining full identifier names
          _ (when verbose
              (println (str "  adding full names to var uses")))
          ctx (assoc ctx
                :var-uses (doall
                            (aiil/add-full-names-to-var-uses ctx)))
          ;; ns file path to var to full-name table -- takes a while
          _ (when verbose
              (println (str "  making ns-path to vars table")))
          ctx (assoc ctx
                :aka-table (aiil/make-ns-path-to-vars-table ctx))
          ;; collect def entries by the file they live in
          _ (when verbose
              (println (str "  making path to defs table")))
          ctx (assoc ctx
                :visit-path-to-defs-table (aiil/make-path-to-defs-table ctx))]
      ;; for each file with def entries, prepare a section and write it out
      (when verbose
        (println (str "* assembling and writing " out-name " file...")))
      ;; using clj-kondo's order is close to classpath order --
      ;; seems to have a few benefits doing it this way
      (cond
        (= format :etags)
        (aiie/create-etags ctx)
        ;;
        (= format :ctags)
        (aiic/create-ctags ctx)
        ;;
        :else ; should not happen
        (throw (Exception.
                 (str "Unrecognized format: " format))))
      (let [end-time (System/currentTimeMillis)]
        (when verbose
          (println (str "-------------------------"))
          (println (str "total duration: " (- end-time start-time) " ms")))
        ;; one use of returning the following is post-mortem examination
        (update ctx
          :times conj [:end-time end-time])))))

(comment

  (let [res (do-it! {:overwrite true
                     :proj-dir (.getPath (cji/file (System/getenv "HOME")
                                           "src" "adorn"))})]
    nil)

  ;; XXX: evaluating any of the following is likely to produce lots of output
  ;;      wrapping in a let or tap> might be helpful

  ;; XXX: should error
  (do-it! {:overwrite true
           :method :shadow-cljs
           :proj-dir (.getPath (cji/file (System/getenv "HOME")
                                 "src" "adorn"))})

  ;; just one file
  (do-it! {:overwrite true
           :paths "src/script.clj" ; XXX: windows paths?
           :proj-dir (.getPath (cji/file (System/getenv "HOME")
                                 "src" "adorn"))})

  (do-it! {:proj-dir (.getPath (cji/file (System/getenv "HOME")
                                 "src" "alc.index-defs"))})

  (do-it! {:proj-dir (.getPath (cji/file (System/getenv "HOME")
                                 "src" "alc.index-defs"))
           :verbose false})

  (do-it! {:overwrite true
           :proj-dir (.getPath (cji/file (System/getenv "HOME")
                                 "src" "alc.index-defs"))})

  (do-it! {:format :ctags
           :overwrite true
           :proj-dir (.getPath (cji/file (System/getenv "HOME")
                                 "src" "alc.index-defs"))})

  (do-it! {:format :ctags
           :out-name ".tags"
           :overwrite true
           :proj-dir (.getPath (cji/file (System/getenv "HOME")
                                 "src" "alc.index-defs"))})

  (do-it! {:overwrite true
           :proj-dir (.getPath (cji/file (System/getenv "HOME")
                                 "src" "alens"))})

  (do-it! {:proj-dir (.getPath (cji/file (System/getenv "HOME")
                                 "src" "antoine"))
           :verbose true})

  (do-it! {:cp-command ["yarn" "shadow-cljs" "classpath"]
           :overwrite true
           :proj-dir (.getPath (cji/file (System/getenv "HOME")
                                 "src" "antoine"))
           :verbose true})

  (do-it! {:method :clj
           :overwrite true
           :proj-dir (.getPath (cji/file (System/getenv "HOME")
                                 "src" "antoine"))})

  (do-it! {:format :ctags
           :overwrite true
           :proj-dir (.getPath (cji/file (System/getenv "HOME")
                                 "src" "antoine"))})

  (do-it! {:overwrite true
           :proj-dir (.getPath (cji/file (System/getenv "HOME")
                                 "src" "antoine"))})

  (require '[clojure.string :as cs])

  (let [m2-repos-path (.getPath (cji/file (System/getenv "HOME")
                                  ".m2" "repository"))
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
        lint-paths (cs/join ":"
                     (concat ["src"] (map (fn [jar-path]
                                            (.getPath (cji/file m2-repos-path
                                                        jar-path)))
                                       jar-paths)))]
    (println "lint-paths:" lint-paths)
    (do-it! {:overwrite true
             :paths lint-paths
             :proj-dir (.getPath (cji/file (System/getenv "HOME")
                                   "src" "antoine"))}))

  ;; XXX: shadow-c)ljs version must be >= 2.8.53
  (do-it! {:overwrite true
           :proj-dir (.getPath (cji/file (System/getenv "HOME")
                                 "src" "atom-chlorine"))})

  (do-it! {:overwrite true
           :proj-dir (.getPath (cji/file (System/getenv "HOME")
                                 "src" "augistints"))})

  (do-it! {:overwrite true
           :proj-dir (.getPath (cji/file (System/getenv "HOME")
                                 "src" "babashka"))})

  (do-it! {:overwrite true
           :proj-dir (.getPath (cji/file (System/getenv "HOME")
                                 "src" "badigeon"))})

  (do-it! {:overwrite true
           :proj-dir (.getPath (cji/file (System/getenv "HOME")
                                 "src" "clj-kondo"))})

  (do-it! {:method :lein
           :overwrite true
           :proj-dir (.getPath (cji/file (System/getenv "HOME")
                                 "src" "clj-kondo"))})

  (do-it! {:overwrite true
           :proj-dir (.getPath (cji/file (System/getenv "HOME")
                                 "src" "cljfmt" "cljfmt"))})

  (do-it! {:overwrite true
           :paths "src/clj/clojure" ; XXX: windows paths?
           :proj-dir (.getPath (cji/file (System/getenv "HOME")
                                 "src" "clojure"))})

  ;; XXX: cannot process clojure clr yet?
  (do-it! {:overwrite true
           :paths "Clojure/Clojure.Source/clojure" ; XXX: windows paths?
           :proj-dir (.getPath (cji/file (System/getenv "HOME")
                                 "src" "clojure-clr"))})

  (do-it! {:overwrite true
           :proj-dir (.getPath (cji/file (System/getenv "HOME")
                                 "src" "clojurescript"))})

  (do-it! {:overwrite true
           :proj-dir (.getPath (cji/file (System/getenv "HOME")
                                 "src" "compliment"))})

  (do-it! {:overwrite true
           :proj-dir (.getPath (cji/file (System/getenv "HOME")
                                 "src" "conch"))})

  (do-it! {:overwrite true
           :proj-dir (.getPath (cji/file (System/getenv "HOME")
                                 "src" "conjure"))})

  (do-it! {:overwrite true
           :proj-dir (.getPath (cji/file (System/getenv "HOME")
                                 "src" "core.async"))})

  ;; project.clj appears broken atm
  (do-it! {:overwrite true
           :proj-dir (.getPath (cji/file (System/getenv "HOME")
                                 "src" "core.logic"))})

  (do-it! {:proj-dir (.getPath (cji/file (System/getenv "HOME")
                                 "src" "debug-repl"))})

  (do-it! {:overwrite true
           :proj-dir (.getPath (cji/file (System/getenv "HOME")
                                 "src" "edamame"))})

  ;; XXX: SNAPSHOT dep in deps.edn causing problems
  (do-it! {:overwrite true
           :proj-dir (.getPath (cji/file (System/getenv "HOME")
                                 "src" "figwheel-main"))})

  (do-it! {:overwrite true
           :proj-dir (.getPath (cji/file (System/getenv "HOME")
                                 "src" "fs"))})

  (do-it! {:overwrite true
           :proj-dir (.getPath (cji/file (System/getenv "HOME")
                                 "src" "jet"))})

  (do-it! {:overwrite true
           :proj-dir (.getPath (cji/file (System/getenv "HOME")
                                 "src" "liquid"))})

  ;; uses boot
  (do-it! {:overwrite true
           :proj-dir (.getPath (cji/file (System/getenv "HOME")
                                 "src" "lumo"))})

  (do-it! {:overwrite true
           :proj-dir (.getPath (cji/file (System/getenv "HOME")
                                 "src" "punk"))})

  (do-it! {:overwrite true
           :proj-dir (.getPath (cji/file (System/getenv "HOME")
                                 "src" "reagent"))})

  (do-it! {:overwrite true
           :proj-dir (.getPath (cji/file (System/getenv "HOME")
                                 "src" "re-frame"))})

  (do-it! {:overwrite true
           :proj-dir (.getPath (cji/file (System/getenv "HOME")
                                 "src" "repl-tooling"))})

  ;; XXX: potemkin makes things hard?
  (do-it! {:overwrite true
           :proj-dir (.getPath (cji/file (System/getenv "HOME")
                                 "src" "rewrite-clj"))})

  (do-it! {:overwrite true
           :proj-dir (.getPath (cji/file (System/getenv "HOME")
                                 "src" "replique"))})

  ;; has shadow-cljs, but should not use that for indexing
  (do-it! {:method :clj
           :overwrite true
           :proj-dir (.getPath (cji/file (System/getenv "HOME")
                                 "src" "shadow-cljs"))})

  ;; has shadow-cljs, but should not use that for indexing
  (let [res (do-it! {:method :clj
                     :overwrite true
                     :proj-dir (.getPath (cji/file (System/getenv "HOME")
                                           "src" "sci"))})]
    nil)

  (let [res (do-it! {:overwrite true
                     :proj-dir (.getPath (cji/file (System/getenv "HOME")
                                           "src" "specter"))})]
    nil)

  (let [res (do-it! {:overwrite true
                     :proj-dir (.getPath (cji/file (System/getenv "HOME")
                                           "src" "tools.deps.alpha"))})]
    nil)

  (let [res (do-it! {:overwrite true
                     :proj-dir (.getPath (cji/file (System/getenv "HOME")
                                           "src" "zprint"))})]
    nil)

  )

;; the manual way
;;
;; sample clj-kondo lint data can be produced like:
;;
;;   clj-kondo \
;;     --lint `clj -Spath` \
;;     --config '{:output {:analysis true :format :edn \
;;                :canonical-paths true}}' \
;;   > clj-kondo-analysis-full-paths.edn
;;
;; see script/make-analysis.sh
;;
;; XXX: running do-it! with an out-of-date analysis data file may cause
;;      problems.  e.g. the current code doesn't try to guard against if
;;      files are shorter or out-of-sync with the analysis.  this could
;;      be made more robust -- perhaps warnings should be emitted at least.
(comment

  (let [proj-dir (.getPath (cji/file (System/getenv "HOME")
                             "src" "adorn"))
        ctx (do-it! {:analysis-path
                     (.getPath (cji/file proj-dir
                                 "clj-kondo-analysis-full-paths.edn"))
                     :overwrite true
                     :proj-dir proj-dir})]
    nil)

  ;; XXX: remember to always regenerate the analysis file before running this
  ;;      as the code base can get out of sync with the analysis
  (let [proj-dir (.getPath (cji/file (System/getenv "HOME")
                             "src" "alc.index-defs"))
        ctx (do-it! {:analysis-path
                     (.getPath (cji/file proj-dir
                                 "clj-kondo-analysis-full-paths.edn"))
                     :format :ctags
                     :overwrite true
                     :proj-dir proj-dir})]
    nil)

  (let [proj-src-dir (.getPath (cji/file (System/getenv "HOME")
                                 "src" "alc.index-defs"))
        psd-mod (-> proj-src-dir
                  java.io.File.
                  .lastModified)
        analysis-file
        (.getPath (cji/file (System/getenv "HOME")
                    "src" "alc.index-defs"
                    "clj-kondo-analysis-full-paths.edn"))
        af-mod (-> analysis-file
                 java.io.File.
                 .lastModified)]
    (when (> psd-mod af-mod)
      (println "project source dir appears newer than analysis file")))

  (let [proj-dir (.getPath (cji/file (System/getenv "HOME")
                             "src" "antoine"))
        ctx (do-it! {:analysis-path
                     (.getPath (cji/file proj-dir
                                 "clj-kondo-analysis-full-paths.edn"))
                     :proj-dir proj-dir})]
    nil)

  (let [proj-dir (.getPath (cji/file (System/getenv "HOME")
                             "src" "clj-kondo"))
        ctx (do-it! {:analysis-path
                     (.getPath (cji/file proj-dir
                                 "clj-kondo-analysis-full-paths.edn"))
                     :overwrite true
                     :proj-dir proj-dir})]
    nil)

  )
