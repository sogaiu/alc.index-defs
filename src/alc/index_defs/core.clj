(ns alc.index-defs.core
  (:require
   [alc.index-defs.bin :as aib]
   [alc.index-defs.fs :as aif]
   [alc.index-defs.seek :as ais]
   [alc.index-defs.table :as ait]
   [alc.index-defs.unzip :as aiu]))

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

;; XXX: move into another file?
;; XXX: use of relative paths might make TAGS file portable?
;; XXX: nix-specific
(defn process-paths
  [defs unzip-root]
  (map (fn [{:keys [:filename] :as entry}]
         (let [[_ jar-path sub-path]
               (re-find #"^([^:]+):([^:]+)$" filename)]
           (if jar-path
             (let [[_ _ jar-name]
                   (re-find #"^(.*/)*([^/]+)$" jar-path)]
               (assert jar-name
                 (str "failed to parse: " jar-path))
               (let [visit-path (str unzip-root "/" jar-name "/" sub-path)]
                 (assoc entry
                   :jar-path jar-path ; XXX: unused atm?
                   :visit-path visit-path)))
             (assoc entry
               :visit-path filename))))
    defs))

;; process-paths should "transform" each definition into a map like:
(comment 

  ;; for something in a file in jar file
  (let [home-dir (System/getenv "HOME")
        unzip-root "/tmp/alc.index-defs"]
    {:filename (str home-dir
                 "/.m2/repository/com/wsscode/pathom/2.2.7/pathom-2.2.7.jar"
                 ":"
                 "com/wsscode/pathom/parser.cljc")
     :row 13
     :col 1
     :ns 'com.wsscode.pathom.parser
     :name 'expr->ast
     :lang :clj
     :visit-path (str unzip-root
                   "/pathom-2.2.7.jar/com/wsscode/pathom/parser.cljc")
     :jar-path (str home-dir
                 "/.m2/repository/com/wsscode/pathom/2.2.7/pathom-2.2.7.jar")})

  ;; for something in a non-jar file
  (let [home-dir (System/getenv "HOME")
        unzip-root "/tmp/alc.index-defs"]
    {:filename (str home-dir
                 "/src/antoine/src/antoine/renderer.cljs")
     :row 64
     :col 1
     :ns 'antoine.renderer
     :name 'init
     :fixed-arities #{0}
     :visit-path (str home-dir
                   "/src/antoine/src/antoine/renderer.cljs")})
  
  )

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

;; make-tag-input-entry-from-src should produce nil or a map like:
(comment

  {:hint "(defn read-string" ; this bit is what requires some work
   :identifier 'read-string
   :line 3805}
  
  )

(defn make-path-to-defs-table
  [defs]
  (reduce (fn [acc {:keys [:visit-path] :as def-entry}]
            (let [bucket (get acc visit-path)]
              (assoc acc
                visit-path (conj (if bucket
                                   bucket
                                   [])
                             def-entry))))
    {}
    defs))

;; make-path-to-defs-table should produce something like:
(comment

  (let [home-dir (System/getenv "HOME")
        unzip-root "/tmp/alc.index-defs"]
    {;; example for something in a file in a jar file
     ;; key is a file path
     (str unzip-root
       "/pathom-2.2.7.jar/com/wsscode/pathom/parser.cljc")
     ;; value is a vector of definition entries
     [{:filename (str home-dir
                   "/.m2/repository/com/wsscode/pathom/2.2.7/pathom-2.2.7.jar"
                   ":"
                   "com/wsscode/pathom/parser.cljc")
       :row 13
       :col 1
       :ns 'com.wsscode.pathom.parser
       :name 'expr->ast
       :lang :clj
       :visit-path (str unzip-root
                     "/pathom-2.2.7.jar/com/wsscode/pathom/parser.cljc")
       :jar-path
       (str home-dir
         "/.m2/repository/com/wsscode/pathom/2.2.7/pathom-2.2.7.jar")}
      ;; likely more definition entries follow
      ]
     ;; example of something in a non-jar file
     ;; key is a file path
     (str home-dir
       "/src/antoine/src/antoine/renderer.cljs")
     ;; value is a vector of definition entries
     [{:filename (str home-dir
                   "/src/antoine/src/antoine/renderer.cljs")
       :row 64
       :col 1
       :ns 'antoine.renderer
       :name 'init
       :fixed-arities #{0}
       :visit-path (str home-dir
                     "/src/antoine/src/antoine/renderer.cljs")}
      ;; likely more definition entries follow
      ]
     ;; likley more key-value pairs follow
     })
  
  )

;; XXX: consider verbose mode for reporting progress as well as timing info
;; XXX: creating one TAGS file for the project source and 
;;      possibly one for all dependencies (or one for each dep)
;;      along with the "include" directive might be intereseting
(defn main
  [lint-path proj-root]
  (let [analysis (:analysis (load-lint lint-path))
        unzip-root (str proj-root "/.alc-id/unzip")
        ;; ensure unzip-root dir exists
        _ (assert (aif/ensure-dir (java.io.File. unzip-root))
            (str "failed to create unzip-root: " unzip-root))
        ;; add visit-path info to def entries 
        new-ns-defs (process-paths (:namespace-definitions analysis)
                      unzip-root)
        new-var-defs (process-paths (:var-definitions analysis)
                       unzip-root)
        all-defs (concat new-ns-defs new-var-defs)
        ;; unzip all jars
        _ (doseq [jar-path (->> all-defs ; all distinct jar paths
                             (keep (fn [{:keys [jar-path]}]
                                     jar-path))
                             distinct)]
            (aiu/unzip-jar jar-path unzip-root))
        ;; collect def entries by the file they live in
        defs-by-visit-path (make-path-to-defs-table all-defs)
        table-path (str proj-root "/TAGS")]
    ;; for each file with def entries, prepare a section and write it out
    (doseq [[f-path def-entries] defs-by-visit-path]
      ;;(println f-path)
      (let [tag-input-entries
            (keep #(make-tag-input-entry-from-src (slurp f-path) %)
              def-entries)
            _ (assert (not (nil? tag-input-entries))
                (str "failed to prepare tag input entries for: " f-path))
            ;;_ (println (first tag-input-entries))
            section (aib/make-section {:file-path f-path
                                       :entries tag-input-entries})
            _ (assert (not (nil? section))
                (str "failed to prepare section for: " f-path))]
        ;; (println (str "writing for: " f-path))
        (ait/write-tags table-path section)))))

;; XXX: running main with an out-of-date lint data file may cause
;;      problems.  e.g. the current code doesn't try to guard against if
;;      files are shorter or out-of-sync with the analysis.  this could
;;      be made more robust -- perhaps warnings should be emitted at least.
;;
;; sample clj-kondo lint data can be produced like:
;;
;;   clj-kondo \
;;     --lint `clj -Spath` \
;;     --config '{:output {:analysis true :format :edn :canonical-paths true}}' \
;;   > clj-kondo-analysis-full-paths.edn
;;
;; see script/make-analysis.sh
(comment

  (let [proj-root (str (System/getenv "HOME")
                    "/src/antoine")]
    (main (str proj-root "/clj-kondo-analysis-full-paths-2.edn")
      proj-root))

  (let [proj-root (str (System/getenv "HOME")
                    "/src/alc.index-defs")]
    (main (str proj-root "/clj-kondo-analysis-full-paths.edn")
      proj-root))

  )

;; a pseudo-record of gradually building up to creating the TAGS file
(comment
  
  (def lint
    (load-lint
      (str (System/getenv "HOME")
        "/src/antoine/clj-kondo-analysis-full-paths-2.edn")))

  (def analysis
    (:analysis lint))

  (def ns-defs
    (:namespace-definitions analysis))

  (def var-defs
    (:var-definitions analysis))

  (let [n-colon-containers
        (->> var-defs
          (filter (fn [entry]
                    (clojure.string/includes? (:filename entry) ":")))
          count)
        n-dot-jar-containers
        (->> var-defs
          (filter (fn [entry]
                    (clojure.string/includes? (:filename entry) ".jar")))
          count)]
    (= n-colon-containers
      n-dot-jar-containers))

  (def unzip-root
    "/tmp/alc.index-defs")

  (def new-var-defs
    (process-paths var-defs unzip-root))

  (def new-ns-defs
    (process-paths ns-defs unzip-root))

  (def all-defs
    (concat new-ns-defs new-var-defs))
  
  ;; unzip all jars
  (let [jar-paths
        (->> all-defs
          (keep (fn [{:keys [jar-path] :as entry}]
                  jar-path))
          distinct)]
    (doseq [jar-path jar-paths]
      (aiu/unzip-jar jar-path unzip-root)))

  (def defs-by-visit-path
    (make-path-to-defs-table all-defs))

  ;; this is it!
  (doseq [[f-path def-entries] defs-by-visit-path]
    ;; XXX
    ;;(println f-path)
    ;; (println (count def-entries))
    (let [tag-input-entries
          (keep #(make-tag-input-entry-from-src (slurp f-path) %)
            def-entries)
          _ (assert (not (nil? tag-input-entries))
              (str "failed to prepare tag input entries for: " f-path))
          ;; XXX
          ;;_ (println (first tag-input-entries))
          section (aib/make-section {:file-path f-path
                                     :entries tag-input-entries})
          _ (assert (not (nil? section))
              (str "failed to prepare section for: " f-path))]
      ;; (println (str "writing for: " f-path))
      ;; (when (= f-path "/tmp/alc.index-defs/clojurescript-1.10.520.jar/cljs/repl/graaljs.clj")
      ;;   (println (:header section))
      ;;   (println (type (:tag-lines section))))
      (ait/write-tags "/tmp/TAGS" section)))

  )

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
      (str (System/getenv "HOME")
        "/src/antoine/src/antoine/renderer.cljs")))

  (count
    (make-tag-input-entries-for-path      
      "/tmp/alc.index-defs/clojurescript-1.10.520.jar/cljs/repl/graaljs.clj"))

  (count
    (make-tag-input-entries-for-path
      "/tmp/alc.index-defs/clojurescript-1.10.520.jar/cljs/core.cljs"))

  )
