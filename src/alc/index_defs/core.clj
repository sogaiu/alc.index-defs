 (ns alc.index-defs.core
  (:require
   [alc.index-defs.analyze :as aia]
   [alc.index-defs.bin :as aib]
   [alc.index-defs.fs :as aif]
   [alc.index-defs.lookup :as ail]
   [alc.index-defs.paths :as aip]
   [alc.index-defs.seek :as ais]
   [alc.index-defs.table :as ait]
   [alc.index-defs.unzip :as aiu]))

;; XXX: move into another file?
;; XXX: defrecords can yield defs where the id doesn't appear in
;;      the source at the point of definition
;;
;;      e.g. (defrecord GraalJSEnv ...) defines:
;;
;;        GraalJSEnv
;;        ->GraalJSEnv
;;        map->GraalJSEnv
(defn make-tag-input-entry-from-src
  [src-str {:keys [:name :row :visit-path] :as def-entry}]
  ;; XXX
  ;;(println (str "src: " (subs src-str 0 30)))
  ;;(println (str "def-entry: " def-entry))
  (let [start-of-hint (ais/seek-to-row src-str row)
        ;; longer strings are more brittle(?)
        ;; XXX: possibly look for second newline?
        new-line-after-hint (clojure.string/index-of src-str
                              "\n" start-of-hint)
        start-of-id (clojure.string/index-of src-str
                      (str name) start-of-hint)]
    ;; XXX
    ;; (println (str "start-of-id: " start-of-id))
    (when (and start-of-id
            new-line-after-hint)
      {:hint (subs src-str
               start-of-hint (if (< 80 (- start-of-id start-of-hint))
                               new-line-after-hint
                               (+ start-of-id (count (str name)))))
       :identifier name
       :line row})))

;; XXX: move into another file?
;; XXX: defrecords can yield defs where the id doesn't appear in
;;      the source at the point of definition
;;
;;      e.g. (defrecord GraalJSEnv ...) defines:
;;
;;        GraalJSEnv
;;        ->GraalJSEnv
;;        map->GraalJSEnv
(defn make-tag-input-entries-from-src
  [src-str {:keys [:name :row :visit-path] :as def-entry} full-fn-names]
  (let [start-of-hint (ais/seek-to-row src-str row)
        ;; longer strings are more brittle(?)
        ;; XXX: possibly look for second newline?
        new-line-after-hint (clojure.string/index-of src-str
                              "\n" start-of-hint)
        ;; XXX: still not quite right
        space-after-hint-start (clojure.string/index-of src-str
                                 " " start-of-hint)
        ;; XXX: not necessarily correct for some very short names (e.g. 'e')
        start-of-id (clojure.string/index-of src-str
                      (str name) space-after-hint-start)]
    (when (and start-of-id
            new-line-after-hint)
      (let [hint (subs src-str
                   start-of-hint (cond
                                   (< 80 (- start-of-id start-of-hint))
                                   new-line-after-hint
                                   ;;
                                   :else
                                   (+ start-of-id (count (str name)))))]
        (distinct
          (conj
            (map (fn [full-name]
                   {:hint hint
                    :identifier full-name
                    :line row})
              full-fn-names)
            {:hint hint
             :identifier name
             :line row}))))))

;; make-tag-input-entries-from-src should produce a sequence of maps like:
(comment

  [{:hint "(defn read-string" ; this bit is what requires some work
    :identifier 'read-string
    :line 973}
   {:hint "(defmacro syntax-quote"
    :identifier 'syntax-quote
    :line 991}]
  
  )

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
                 :ns-defs (aip/process-ns-defs ctx))
           ctx (assoc ctx
                 :var-defs (aip/process-var-defs ctx))
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
                     #(make-tag-input-entries-from-src src-str
                        % (get synonyms-table (:name %))))
                   ;;distinct
                   ))
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

  ;; XXX: shadow-cljs version must be >= 2.8.5x (not sure exactly)
  (main {:overwrite true
         :proj-dir (aif/path-join (System/getenv "HOME")
                     "src/atom-chlorine")})

  (main {:overwrite true
         :paths "src/clj/clojure"
         :proj-dir (aif/path-join (System/getenv "HOME")
                      "src/clojure")})

  (main {:proj-dir (aif/path-join (System/getenv "HOME")
                     "src/debug-repl")})

  (main {:proj-dir (aif/path-join (System/getenv "HOME")
                     "src/alc.index-defs")})

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
                     "src/alc.index-defs")
         :verbose false})

  (main {:overwrite true
         :proj-dir (aif/path-join (System/getenv "HOME")
                     "src/augistints")})

  (main {:overwrite true
         :proj-dir (aif/path-join (System/getenv "HOME")
                     "src/adorn")})

  ;; just one file
  (main {:overwrite true
         :paths "src/script.clj"
         :proj-dir (aif/path-join (System/getenv "HOME")
                     "src/adorn")})

  ;; XXX: should error
  (main {:overwrite true
         :method :shadow-cljs
         :proj-dir (aif/path-join (System/getenv "HOME")
                     "src/adorn")})

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

  (main {:proj-dir (aif/path-join (System/getenv "HOME")
                     "src/antoine")
         :verbose true})

  (main {:method :lein
         :overwrite true
         :proj-dir (aif/path-join (System/getenv "HOME")
                     "src/clj-kondo")})

  (main {:overwrite true
         :proj-dir (aif/path-join (System/getenv "HOME")
                     "src/clj-kondo")})

  )

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
                    "src/antoine")]
    (main {:analysis-path
           (aif/path-join proj-dir
             "clj-kondo-analysis-full-paths-2.edn")
           :proj-dir proj-dir}))

  (let [proj-dir (aif/path-join (System/getenv "HOME")
                    "src/alc.index-defs")]
    (main {:analysis-path
           (aif/path-join proj-dir
             "clj-kondo-analysis-full-paths.edn")
           :proj-dir proj-dir}))

  (let [proj-dir (aif/path-join (System/getenv "HOME")
                    "src/adorn")]
    (main {:analysis-path
           (aif/path-join proj-dir
             "clj-kondo-analysis-full-paths.edn")
           :overwrite true
           :proj-dir proj-dir}))

  )

(defn -main
  [& args]
  (let [[front-str & _] args
        front (when front-str
                (read-string front-str))
        opts {:proj-dir (if (string? front)
                          front
                          (System/getProperty "user.dir"))}
        opts (merge opts
               (if (map? front)
                 front
                 {}))]
    (main opts))
  (flush)
  (System/exit 0))

;; how do var-defs and ns-defs differ in visit-path?
(comment

  (let [ns-defs-set (->> (:ns-defs ctx)
                      (map :visit-path)
                      set)]
    (doseq [{:keys [:visit-path]} (:var-defs ctx)]
      (when (not (contains? ns-defs-set
                   visit-path))
        (println visit-path))))

  ;; conclusion:
  ;;
  ;;   var-defs can have more elements because some files don't
  ;;   have ns forms (e.g. clojure/core_print.clj has in-ns at the top)

  )
