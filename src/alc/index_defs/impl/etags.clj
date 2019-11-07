(ns alc.index-defs.impl.etags
  (:require
   [alc.index-defs.impl.bin :as aiib]
   [alc.index-defs.impl.fs :as aiif]
   [alc.index-defs.impl.seek :as aiis]
   [alc.index-defs.impl.tags :as aiit]))

;; XXX: defrecords can yield defs where the id doesn't appear in
;;      the source at the point of definition
;;
;;      e.g. (defrecord GraalJSEnv ...) defines:
;;
;;        GraalJSEnv
;;        ->GraalJSEnv
;;        map->GraalJSEnv
(defn make-etag-row-entries-from-src
  [src-str {:keys [:name :row]} full-names]
  (let [start-of-hint (aiis/seek-to-row src-str row)
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
                    :row row})
              full-names)
            {:hint hint
             :identifier name
             :row row}))))))

;; make-etag-row-entries-from-src should produce a sequence of maps like:
(comment

  [{:hint "(defn read-string" ; this bit is what requires some work
    :identifier 'read-string
    :row 973}
   {:hint "(defmacro syntax-quote"
    :identifier 'syntax-quote
    :row 991}]

  )

(defn create-etags
  [{:keys [:aka-table :ns-defs :proj-dir :table-path :var-defs
           :visit-path-to-defs-table]}]
  (doseq [visit-path (->> (concat ns-defs var-defs)
                       (map (fn [{:keys [:visit-path]}]
                              visit-path))
                       distinct)]
    (let [;; try to use relative paths in TAGS files
          ;; XXX: consider symlinking (e.g. ~/.gitlibs/ things) to
          ;;      make everything appear under proj-dir?
          file-path (aiif/try-relativize visit-path
                      [proj-dir
                       (.getCanonicalPath (java.io.File. proj-dir))])
          def-entries (get visit-path-to-defs-table visit-path)
          synonyms-table (get aka-table visit-path)
          src-str (aiif/get-content visit-path)
          etag-row-entries
          (->> def-entries
            (mapcat
              #(make-etag-row-entries-from-src src-str
                 % (get synonyms-table (:name %))))
            distinct
            doall)
          _ (assert (not (nil? etag-row-entries))
              (str "failed to prepare tag input entries for: " visit-path))
          section (aiib/make-etags-section {:file-path file-path
                                           :entries etag-row-entries})
          _ (assert (not (nil? section))
              (str "failed to prepare section for: " visit-path))]
      (aiit/write-tags table-path section))))
