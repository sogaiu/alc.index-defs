 (ns alc.index-defs.lookup)

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
