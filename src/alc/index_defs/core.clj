 (ns alc.index-defs.core
  (:require
   [alc.index-defs.analyze :as aia]
   [alc.index-defs.bin :as aib]
   [alc.index-defs.fs :as aif]
   [alc.index-defs.lookup :as ail]
   [alc.index-defs.seek :as ais]
   [alc.index-defs.opts :as aio]
   [alc.index-defs.table :as ait]
   [alc.index-defs.unzip :as aiu]))

;; XXX: creating one TAGS file for the project source and 
;;      possibly one for all dependencies (or one for each dep)
;;      along with the "include" directive might be intereseting
(defn main
  ([{:keys [:analysis-path :format :method :out-name
            :overwrite :paths :proj-dir :verbose]
     :or {format :etags
          overwrite false
          verbose true}}]
   (when verbose
     (println "[alc.index-defs - index file creator]"))
   (assert proj-dir
     ":proj-dir is required")
   (let [out-name (cond
                   out-name
                   out-name
                   ;;
                   (= format :ctags)
                   "tags"
                   ;;
                   (= format :etags)
                   "TAGS"
                   ;;
                   :else
                   (throw (Exception.
                            (str "Unrecognized format: " format))))
         table-path (aif/path-join proj-dir out-name)
         tags-file (java.io.File. table-path)]
     (if (not overwrite)
       (assert (not (.exists tags-file))
         (str "TAGS already exists for: " proj-dir))
       (when (.exists tags-file)
         (let [result (.delete tags-file)]
           (assert result
             (str "failed to remove TAGS file for: " proj-dir)))))
     (let [ctx {:proj-dir proj-dir
                ;; XXX: store other things such as options too?
                :times [[:start-time (System/currentTimeMillis)]]}
           ctx (if analysis-path
                 (let [opts {:verbose verbose}
                       analysis (aia/load-analysis analysis-path opts)]
                   ;; XXX: lint-paths unavailable
                   (assoc ctx
                     :analysis analysis))
                 (let [opts {:method method
                             :verbose verbose}
                       opts (if paths ; don't use any 'method'
                              (cond-> (assoc opts
                                        :paths paths)
                                method (dissoc :method))
                              opts)
                       [results lint-paths]
                       (aia/study-project-and-deps proj-dir opts)]
                   (assert results
                     (str "analysis failed"))
                   (assoc ctx
                     :analysis (:analysis results)
                     :lint-paths lint-paths)))
           ctx (assoc ctx
                 :unzip-root (aif/path-join
                               (aif/path-join proj-dir ".alc-id")
                               "unzip"))
           unzip-root (:unzip-root ctx)
           ;; ensure unzip-root dir exists
           _ (assert (aif/ensure-dir
                       (java.io.File. unzip-root))
               (str "failed to create unzip-root: " unzip-root))
           ctx (assoc ctx
                 :ns-defs (ail/process-ns-defs ctx))
           ctx (assoc ctx
                 :var-defs (ail/process-var-defs ctx))
           ;; unzip all jars
           _ (when verbose
               (println (str "* unzipping jars...")))
           ;; all distinct jar paths
           ;; XXX: redundant to be looking at var-defs?
           _ (doseq [jar-path (->> (concat (:ns-defs ctx) (:var-defs ctx))
                                (keep (fn [{:keys [jar-path]}]
                                        jar-path))
                                distinct)]
               (aiu/unzip-jar jar-path unzip-root))
           _ (when verbose
               (println (str "* massaging analysis data...")))
           ;; visit-path to ns-name table
           ctx (assoc ctx
                 :path-to-ns-table (ail/make-path-to-ns-name-table ctx))
           ;; ns file path to var to full-fn-name table -- takes a while
           ctx (assoc ctx
                 :aka-table (ail/make-ns-path-to-vars-table ctx))
           ;; collect def entries by the file they live in
           ctx (assoc ctx
                 :visit-path-to-defs-table (ail/make-path-to-defs-table ctx))]
       ;; for each file with def entries, prepare a section and write it out
       (when verbose
         (println (str "* assembling and writing " out-name
                    " file...")))
       ;; using clj-kondo's order is close to classpath order --
       ;; seems to have a few benefits doing it this way
       (doseq [visit-path (distinct
                            (map (fn [{:keys [:visit-path]}]
                                   visit-path)
                              (concat (:ns-defs ctx) (:var-defs ctx))))]
         (let [def-entries (get (:visit-path-to-defs-table ctx) visit-path)
               synonyms-table (get (:aka-table ctx) visit-path)
               src-str (slurp visit-path)
               tag-input-entries
               (doall
                 (->> def-entries
                   (mapcat
                     #(ail/make-tag-input-entries-from-src src-str
                        % (get synonyms-table (:name %))))
                   distinct))
               _ (assert (not (nil? tag-input-entries))
                   (str "failed to prepare tag input entries for: " visit-path))
               ;; try to use relative paths in TAGS files
               ;; XXX: consider symlinking (e.g. ~/.gitlibs/ things) to
               ;;      make everything appear under proj-dir?
               file-path (let [cp (.getCanonicalPath
                                    (java.io.File. proj-dir))]
                           (cond
                             (clojure.string/starts-with? visit-path
                               proj-dir)
                             (aif/path-split visit-path proj-dir)
                             ;;
                             (clojure.string/starts-with? visit-path cp)
                             (aif/path-split visit-path cp)
                             ;;
                             :else
                             visit-path))
               section (aib/make-section {:file-path file-path
                                          :format format
                                          :entries tag-input-entries})
               _ (assert (not (nil? section))
                   (str "failed to prepare section for: " visit-path))]
           (ait/write-tags table-path section)))
       (when (= format :ctags) ; better to be sorted for ctags
         (when verbose
           (println "* sorting ctags format file..."))
         (ait/sort-tags table-path))
       (let [duration (- (System/currentTimeMillis) (-> (:times ctx)
                                                      (nth 0)
                                                      (nth 1)))]
         (when verbose
           ;; (println (str "  duration: "
           ;;            (- (System/currentTimeMillis) post-massaging-time)
           ;;            " ms"))
           (println (str "-------------------------"))
           (println (str "total duration: " duration " ms"))))))))

(comment

  (main {:overwrite true
         :proj-dir (aif/path-join (System/getenv "HOME")
                     "src/adorn")})

  ;; XXX: should error
  (main {:overwrite true
         :method :shadow-cljs
         :proj-dir (aif/path-join (System/getenv "HOME")
                     "src/adorn")})

  ;; just one file
  (main {:overwrite true
         :paths "src/script.clj"
         :proj-dir (aif/path-join (System/getenv "HOME")
                     "src/adorn")})

  (main {:proj-dir (aif/path-join (System/getenv "HOME")
                     "src/alc.index-defs")})

  (main {:proj-dir (aif/path-join (System/getenv "HOME")
                     "src/alc.index-defs")
         :verbose false})

  (main {:overwrite true
         :proj-dir (aif/path-join (System/getenv "HOME")
                     "src/alc.index-defs")})

  (main {:format :ctags
         :overwrite true
         :proj-dir (aif/path-join (System/getenv "HOME")
                     "src/alc.index-defs")})

  (main {:format :ctags
         :out-name ".tags"
         :overwrite true
         :proj-dir (aif/path-join (System/getenv "HOME")
                     "src/alc.index-defs")})

  (main {:proj-dir (aif/path-join (System/getenv "HOME")
                     "src/antoine")
         :verbose true})

  (main {:method :clj
         :overwrite true
         :proj-dir (aif/path-join (System/getenv "HOME")
                     "src/antoine")})

  (main {:format :ctags
         :overwrite true
         :proj-dir (aif/path-join (System/getenv "HOME")
                     "src/antoine")})

  (main {:overwrite true
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
    (main {:overwrite true
           :paths lint-paths
           :proj-dir (aif/path-join (System/getenv "HOME")
                       "src/antoine")}))

  ;; XXX: shadow-cljs version must be >= 2.8.53
  (main {:overwrite true
         :proj-dir (aif/path-join (System/getenv "HOME")
                     "src/atom-chlorine")})

  (main {:overwrite true
         :proj-dir (aif/path-join (System/getenv "HOME")
                     "src/augistints")})

  (main {:overwrite true
         :proj-dir (aif/path-join (System/getenv "HOME")
                     "src/clj-kondo")})

  (main {:method :lein
         :overwrite true
         :proj-dir (aif/path-join (System/getenv "HOME")
                     "src/clj-kondo")})

  (main {:overwrite true
         :paths "src/clj/clojure"
         :proj-dir (aif/path-join (System/getenv "HOME")
                      "src/clojure")})

  (main {:proj-dir (aif/path-join (System/getenv "HOME")
                     "src/debug-repl")})

  ;; uses boot
  (main {:overwrite true
         :proj-dir (aif/path-join (System/getenv "HOME")
                     "src/lumo")})

  (main {:format :ctags
         :overwrite true
         :proj-dir (aif/path-join (System/getenv "HOME")
                     "src/repl-tooling")})

  ;; has shadow-cljs, but should not use that for indexing
  (main {:method :clj
         :overwrite true
         :proj-dir (aif/path-join (System/getenv "HOME")
                     "src/sci")})

  )

;; XXX: may no longer work because clj-kondo doesn't retain full-fn-name
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
;; XXX: running main with an out-of-date lint data file may cause
;;      problems.  e.g. the current code doesn't try to guard against if
;;      files are shorter or out-of-sync with the analysis.  this could
;;      be made more robust -- perhaps warnings should be emitted at least.
(comment

  (let [proj-dir (aif/path-join (System/getenv "HOME")
                    "src/adorn")]
    (main {:analysis-path
           (aif/path-join proj-dir
             "clj-kondo-analysis-full-paths.edn")
           :overwrite true
           :proj-dir proj-dir}))

  (let [proj-dir (aif/path-join (System/getenv "HOME")
                    "src/alc.index-defs")]
    (main {:analysis-path
           (aif/path-join proj-dir
             "clj-kondo-analysis-full-paths.edn")
           :proj-dir proj-dir}))

  (let [proj-dir (aif/path-join (System/getenv "HOME")
                    "src/antoine")]
    (main {:analysis-path
           (aif/path-join proj-dir
             "clj-kondo-analysis-full-paths-2.edn")
           :proj-dir proj-dir}))

  )

(defn -main
  [& args]
  (let [opts {:proj-dir
              (if-let [first-str-opt (->> args
                                       (keep #(string? (read-string %)))
                                       first)]
                first-str-opt
                (System/getProperty "user.dir"))}
        opts (merge opts
               (aio/merge-only-map-strs args))]
    (main opts))
  (flush)
  (System/exit 0))
