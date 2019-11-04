(ns alc.index-defs.lookup
  (:require
   [alc.index-defs.fs :as aif]
   [alc.index-defs.seek :as ais]
   [edamame.core :as ec]))

(defn add-paths-to
  [entries unzip-root]
  (let [split-path-re #"(?x)    # free-form
                        ^       # start with
                        ([^:]+) # a path to a file (.jar)
                        :       # separated by a colon
                        ([^:]+) # and then a path contained within
                        $       # and nothing else "]
    (map
      (fn [{:keys [:filename] :as entry}]
        (let [[_ jar-path sub-path]
              (re-find split-path-re filename)]
          (if jar-path
            (let [jar-name (.getName (java.io.File. jar-path))]
              (assert jar-name
                (str "failed to parse: " jar-path))
              (let [visit-path (aif/path-join
                                 (aif/path-join unzip-root jar-name)
                                 sub-path)]
                (assoc entry
                  :jar-path jar-path
                  :visit-path visit-path)))
            (assoc entry
              :visit-path filename))))
      entries)))

;; XXX: possibly instead of doing this 3 times, may be a single table can
;;      be made to be used to look things up for all three cases
(defn add-paths-to-ns-defs
  [{:keys [:analysis :unzip-root]}]
  (add-paths-to (:namespace-definitions analysis)
    unzip-root))

(defn add-paths-to-var-defs
  [{:keys [:analysis :unzip-root]}]
  (add-paths-to (:var-definitions analysis)
    unzip-root))

(defn add-paths-to-var-uses
  [{:keys [:analysis :unzip-root]}]
  (add-paths-to (:var-usages analysis)
    unzip-root))

(comment

  ;; sci commit bfa2d67b21171d1be55e49ccb0c8e0b8031ab761
  ;;
  ;; from :analysis -> :var-usages:
  ;;
  ;; {:arity 2
  ;;  :col 18
  ;;  :filename (aif/path-join (System/getenv "HOME")
  ;;              "src/sci/src/sci/impl/interpreter.cljc")
  ;;  :fixed-arities #{2}
  ;;  :from sci.impl.interpreter
  ;;  :lang :clj
  ;;  :name macroexpand
  ;;  :row 61
  ;;  :to sci.impl.macros}
  (let [src-str (slurp (aif/path-join (System/getenv "HOME")
                         "src/sci/src/sci/impl/interpreter.cljc"))
        spot (subs src-str (ais/seek-to-row-col src-str 61 18))]
    (println (subs spot 0 10))
    (println (ec/parse-string spot)))

  ;; {:arity 2
  ;;  :col 15
  ;;  :filename (aif/path-join (System/getenv "HOME")
  ;;              "src/sci/src/sci/impl/interpreter.cljc"
  ;;  :fixed-arities #{2}
  ;;  :from sci.impl.interpreter
  ;;  :lang :clj
  ;;  :name interpret
  ;;  :row 28
  ;;  :to sci.impl.interpreter}
  (let [src-str (slurp (aif/path-join (System/getenv "HOME")
                         "src/sci/src/sci/impl/interpreter.cljc"))
        spot (subs src-str (ais/seek-to-row-col src-str 28 15))]
    (println (subs spot 0 10))
    (println (ec/parse-string spot)))

  ;; clojure commit 653b8465845a78ef7543e0a250078eea2d56b659
  ;;
  ;; {:arity 3
  ;;  :col 7
  ;;  :filename "src/clojure/src/clj/clojure/java/io.clj"
  ;;  :fixed-arities #{3}
  ;;  :from clojure.java.io
  ;;  :name replace
  ;;  :row 41
  ;;  :to clojure.string}
  (let [src-str (slurp (aif/path-join (System/getenv "HOME")
                         "src/clojure/src/clj/clojure/java/io.clj"))
        spot (subs src-str (ais/seek-to-row-col src-str 41 7))]
    (println (subs spot 0 10))
    (println (ec/parse-string spot)))

  )

(defn add-full-names-to-var-uses
  [{:keys [:var-uses]}]
  (map
    (fn [{:keys [:col :name :row :visit-path] :as entry}]
      (if (and col row visit-path)
        (let [src-str (aif/get-content visit-path)
              spot (subs src-str (ais/seek-to-row-col src-str row col))
              full-name (when (not= name 'fn*)
                          (try
                            ;; XXX: need to customize parsing?
                            (ec/parse-string spot {:quote true})
                            (catch Exception e
                              ;;
                              (println "exception:"
                                (get-in (Throwable->map e) [:via 0 :message]))
                              (println "entry:" entry))
                            (finally :failed-to-parse)))]
          (if full-name
            (assoc entry
              :full-name full-name)
            entry))
        entry))
    var-uses))

(defn make-path-to-ns-name-table
  [{:keys [:ns-defs]}]
  (into {}
    (for [{:keys [:name :visit-path]} ns-defs]
      [visit-path name])))

(defn make-ns-path-to-vars-table
  [{:keys [:path-to-ns-table :var-uses]}]
  (apply merge-with
    (fn [m1 m2]
      (merge-with into
        m1 m2))
    (for [[ns-path ns-name] path-to-ns-table
          {:keys [:full-name :name :to]} var-uses
          :when (and full-name
                  (= ns-name to))]
      {ns-path {name #{full-name}}})))

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

;; XXX: defrecords can yield defs where the id doesn't appear in
;;      the source at the point of definition
;;
;;      e.g. (defrecord GraalJSEnv ...) defines:
;;
;;        GraalJSEnv
;;        ->GraalJSEnv
;;        map->GraalJSEnv
(defn make-tag-input-entries-from-src
  [src-str {:keys [:name :row]} full-names]
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
              full-names)
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
