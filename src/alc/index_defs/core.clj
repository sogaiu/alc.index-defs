 (ns alc.index-defs.core
  (:require
   [alc.index-defs.analyze :as aia]
   [alc.index-defs.bin :as aib]
   [alc.index-defs.fs :as aif]
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

(defn make-path-to-ns-name-table
  [{:keys [:ns-defs]}]
  (into {}
    (for [{:keys [:name :visit-path]} ns-defs]
      [visit-path name])))

(defn make-ns-path-to-vars-table
  [{:keys [:analysis :path-to-ns-table]}]
  (let [usages (:var-usages analysis)]
    (apply merge-with
      (fn [m1 m2]
        (merge-with into
          m1 m2))
      (for [[ns-path ns-name] path-to-ns-table
            {:keys [:full-fn-name :name :to]} usages
            :when (and full-fn-name
                    (= ns-name to))]
        {ns-path {name #{full-fn-name}}}))))

(defn make-path-to-defs-table
  [{:keys [:ns-defs :var-defs]}]
  (apply merge-with
    into
    (for [{:keys [:visit-path] :as def-entry}
          (concat ns-defs var-defs)]
      {visit-path [def-entry]})))

;; make-path-to-defs-table should produce something like:
(comment

  (let [home-dir (System/getenv "HOME")
        unzip-root "/tmp/alc.index-defs"]
    {;; example for something in a file in a jar file
     ;; key is a file path
     (aif/path-join unzip-root
       "pathom-2.2.7.jar/com/wsscode/pathom/parser.cljc")
     ;; value is a vector of definition entries
     [{:filename (aif/path-join home-dir
                   (str ".m2/repository/"
                     "com/wsscode/pathom/2.2.7/pathom-2.2.7.jar"
                     ":"
                     "com/wsscode/pathom/parser.cljc"))
       :row 13
       :col 1
       :ns 'com.wsscode.pathom.parser
       :name 'expr->ast
       :lang :clj
       :visit-path (aif/path-join unzip-root
                     "pathom-2.2.7.jar/com/wsscode/pathom/parser.cljc")
       :jar-path
       (aif/path-join home-dir
         ".m2/repository/com/wsscode/pathom/2.2.7/pathom-2.2.7.jar")}
      ;; likely more definition entries follow
      ]
     ;; example of something in a non-jar file
     ;; key is a file path
     (aif/path-join home-dir
       "src/antoine/src/antoine/renderer.cljs")
     ;; value is a vector of definition entries
     [{:filename (aif/path-join home-dir
                   "src/antoine/src/antoine/renderer.cljs")
       :row 64
       :col 1
       :ns 'antoine.renderer
       :name 'init
       :fixed-arities #{0}
       :visit-path (aif/path-join home-dir
                     "src/antoine/src/antoine/renderer.cljs")}
      ;; likely more definition entries follow
      ]
     ;; likley more key-value pairs follow
     })
  
  )

;; XXX: creating one TAGS file for the project source and 
;;      possibly one for all dependencies (or one for each dep)
;;      along with the "include" directive might be intereseting
;;
;; XXX: make main just take single argument - opts
(defn main
  ([proj-root]
   (main proj-root nil))
  ([proj-root {:keys [:analysis-path :method :overwrite :paths :verbose]
               :or {overwrite false
                    verbose true}}]
   (when verbose
     (println "[alc.index-defs - index file creator]"))
   (let [table-path (aif/path-join proj-root "TAGS")
         tags-file (java.io.File. table-path)]
     (if (not overwrite)
       (assert (not (.exists tags-file))
         (str "TAGS already exists for: " proj-root))
       (when (.exists tags-file)
         (let [result (.delete tags-file)]
           (assert result
             (str "failed to remove TAGS file for: " proj-root)))))
     (let [ctx {:proj-root proj-root
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
                       (aia/study-project-and-deps proj-root opts)]
                   (assert results
                     (str "analysis failed"))
                   (assoc ctx
                     :analysis (:analysis results)
                     :lint-paths lint-paths)))
           ctx (assoc ctx
                 :unzip-root (aif/path-join
                               (aif/path-join proj-root ".alc-id")
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
                 :path-to-ns-table (make-path-to-ns-name-table ctx))
           ;; ns file path to var to full-fn-name table -- takes a while
           ctx (assoc ctx
                 :aka-table (make-ns-path-to-vars-table ctx))
           ;; collect def entries by the file they live in
           ctx (assoc ctx
                 :visit-path-to-defs-table (make-path-to-defs-table ctx))]
       ;; for each file with def entries, prepare a section and write it out
       (when verbose
         (println (str "* assembling and writing TAGS file...")))
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
               ;;      make everything appear under proj-root?
               file-path (let [cp (.getCanonicalPath
                                    (java.io.File. proj-root))]
                           (cond
                             (clojure.string/starts-with? visit-path
                               proj-root)
                             (aif/path-split visit-path proj-root)
                             ;;
                             (clojure.string/starts-with? visit-path cp)
                             (aif/path-split visit-path cp)
                             ;;
                             :else
                             visit-path))
               section (aib/make-section {:file-path file-path
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

  (main (aif/path-join (System/getenv "HOME")
          "src/atom-chlorine")
    {:overwrite true})

  (main (aif/path-join (System/getenv "HOME")
          "src/clojure")
    {:overwrite true
     :paths "src/clj/clojure"})

  (main (aif/path-join (System/getenv "HOME")
          "src/debug-repl"))

  (main (aif/path-join (System/getenv "HOME")
          "src/alc.index-defs"))

  (main (aif/path-join (System/getenv "HOME")
          "src/alc.index-defs")
    {:overwrite true})

  (main (aif/path-join (System/getenv "HOME")
          "src/alc.index-defs")
    {:verbose false})

  (main (aif/path-join (System/getenv "HOME")
          "src/augistints")
    {:overwrite true})

  (main (aif/path-join (System/getenv "HOME")
          "src/adorn")
    {:overwrite true})

  ;; just one file
  (main (aif/path-join (System/getenv "HOME")
          "src/adorn")
    {:overwrite true
     :paths "src/script.clj"})

  ;; XXX: should error
  (main (aif/path-join (System/getenv "HOME")
          "src/adorn")
    {:overwrite true
     :method :shadow-cljs})

  (main (aif/path-join (System/getenv "HOME")
          "src/antoine")
    {:method :clj
     :overwrite true})

  (main (aif/path-join (System/getenv "HOME")
          "src/antoine")
    {:overwrite true})

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
    (main (aif/path-join (System/getenv "HOME")
            "src/antoine")
      {:overwrite true
       :paths lint-paths}))

  (main (aif/path-join (System/getenv "HOME")
          "src/antoine")
    {:verbose true})

  (main (aif/path-join (System/getenv "HOME")
          "src/clj-kondo")
    {:overwrite true
     :method :lein})

  (main (aif/path-join (System/getenv "HOME")
          "src/clj-kondo")
    {:overwrite true})

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

  (let [proj-root (aif/path-join (System/getenv "HOME")
                    "src/antoine")]
    (main proj-root
      {:analysis-path
       (aif/path-join proj-root
         "clj-kondo-analysis-full-paths-2.edn")}))

  (let [proj-root (aif/path-join (System/getenv "HOME")
                    "src/alc.index-defs")]
    (main proj-root
      {:analysis-path
       (aif/path-join proj-root
         "clj-kondo-analysis-full-paths.edn")}))

  (let [proj-root (aif/path-join (System/getenv "HOME")
                    "src/adorn")]
    (main proj-root
      {:analysis-path
       (aif/path-join proj-root
         "clj-kondo-analysis-full-paths.edn")
       :overwrite true}))

  (let [proj-root (aif/path-join (System/getenv "HOME")
                    "src/adorn")]
    (main proj-root
      {:lang :clj
       :analysis-path
       (aif/path-join proj-root
         "clj-kondo-analysis-full-paths.edn")
       :overwrite true}))

  )

(defn -main [& args]
  (let [[proj-root & other] args
        [opts & _] other
        opts (when opts
               (read-string opts))]
    (main proj-root
      (when (map? opts)
        opts)))
  (flush)
  (System/exit 0))

;; debugging-related
(comment

  ;; XXX: all-defs needs to be appropriately defined first
  (defn make-tag-input-entries-for-path
    [f-path]
    (make-tag-input-entries-from-src (slurp f-path)
      (filter (fn [{:keys [:visit-path]}]
                (= visit-path f-path))
        all-defs)))

  (count
    (make-tag-input-entries-for-path
      (aif/path-join (System/getenv "HOME")
        "src/antoine/src/antoine/renderer.cljs")))

  (count
    (make-tag-input-entries-for-path      
      "/tmp/alc.index-defs/clojurescript-1.10.520.jar/cljs/repl/graaljs.clj"))

  (count
    (make-tag-input-entries-for-path
      "/tmp/alc.index-defs/clojurescript-1.10.520.jar/cljs/core.cljs"))

  )

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
