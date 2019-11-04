(ns alc.index-defs.tags
  (:require
   [alc.index-defs.bin :as aib]
   [alc.index-defs.fs :as aif]
   [alc.index-defs.lookup :as ail]
   [clojure.java.io :as cji]))

(defn sort-tags
  [file-path]
  (let [tmp-path (str file-path ".tmp.alcid")] ; XXX: should check existence
    (with-open [r (cji/reader file-path)]
      (with-open [w (cji/writer tmp-path)]
        (doseq [line (sort (line-seq r))]
          (.write w line)
          (.newLine w))
        (.flush w)))
    (cji/delete-file (cji/file file-path))
    (.renameTo (cji/file tmp-path) (cji/file file-path))))

(defn write-tags
  [file-path section]
  (spit file-path
    (.toString section) :append true))

(defn create-etags
  [{:keys [:aka-table :format :ns-defs :proj-dir :table-path :var-defs
           :visit-path-to-defs-table]}]
  (doseq [visit-path (distinct
                       (map (fn [{:keys [:visit-path]}]
                              visit-path)
                         (concat ns-defs var-defs)))]
    (let [def-entries (get visit-path-to-defs-table visit-path)
          synonyms-table (get aka-table visit-path)
          src-str (aif/get-content visit-path)
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
          file-path (aif/try-relativize visit-path
                      [proj-dir
                       (.getCanonicalPath (java.io.File. proj-dir))])
          section (aib/make-section {:file-path file-path
                                     :format format
                                     :entries tag-input-entries})
          _ (assert (not (nil? section))
              (str "failed to prepare section for: " visit-path))]
      (write-tags table-path section))))

(defn create-ctags
  [{:keys [:aka-table :format :ns-defs :proj-dir :table-path :var-defs
           :verbose :visit-path-to-defs-table]}]
  (doseq [visit-path (distinct
                       (map (fn [{:keys [:visit-path]}]
                              visit-path)
                         (concat ns-defs var-defs)))]
    (let [def-entries (get visit-path-to-defs-table visit-path)
          synonyms-table (get aka-table visit-path)
          tag-input-entries
          (doall
            (->> def-entries
              (mapcat (fn [{:keys [:name :row]}]
                        (map (fn [synonym]
                               {:identifier synonym
                                :line row})
                          (get synonyms-table name))))
              distinct))
          _ (assert (not (nil? tag-input-entries))
              (str "failed to prepare tag input entries for: " visit-path))
          ;; try to use relative paths
          ;; XXX: consider symlinking (e.g. ~/.gitlibs/ things) to
          ;;      make everything appear under proj-dir?
          file-path (aif/try-relativize visit-path
                      [proj-dir
                       (.getCanonicalPath (java.io.File. proj-dir))])
          ;; XXX: ctags files don't really have sections
          section (aib/make-section {:file-path file-path
                                     :format format
                                     :entries tag-input-entries})
          _ (assert (not (nil? section))
              (str "failed to prepare section for: " visit-path))]
      (write-tags table-path section)))
  (when verbose
    (println "* sorting ctags format file..."))
  (sort-tags table-path))
