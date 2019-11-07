(ns alc.index-defs.impl.ctags
  (:require
   [alc.index-defs.impl.bin :as aiib]
   [alc.index-defs.impl.fs :as aiif]
   [alc.index-defs.impl.tags :as aiit]
   [clojure.java.io :as cji]))

(defn create-ctags
  [{:keys [:aka-table :ns-defs :proj-dir :table-path :var-defs
           :verbose :visit-path-to-defs-table]}]
  (doseq [visit-path (->> (concat ns-defs var-defs)
                       (map (fn [{:keys [:visit-path]}]
                              visit-path))
                       distinct)]
    (let [;; try to use relative paths
          ;; XXX: consider symlinking (e.g. ~/.gitlibs/ things) to
          ;;      make everything appear under proj-dir?
          file-path (aiif/try-relativize visit-path
                      [proj-dir
                       (.getCanonicalPath (java.io.File. proj-dir))])
          def-entries (get visit-path-to-defs-table visit-path)
          synonyms-table (get aka-table visit-path)
          ctag-row-entries
          (->> def-entries
            (mapcat (fn [{:keys [:name :row]}]
                      (map (fn [synonym]
                             {:file-path file-path
                              :identifier synonym
                              :row row})
                        (get synonyms-table name))))
            distinct
            doall)
          _ (assert (not (nil? ctag-row-entries))
              (str "failed to prepare ctag-row-entries for: " visit-path))
          ctags-rows (StringBuilder.)
          _ (doseq [ctag-row-entry ctag-row-entries]
              (aiib/append-ctags-row ctags-rows ctag-row-entry))
          _ (assert (not (nil? ctags-rows))
              (str "failed to prepare ctags-rows for: " visit-path))]
      (aiit/write-tags table-path ctags-rows)))
  (when verbose
    (println "* sorting ctags format file..."))
  (aiit/sort-tags table-path))

(defn read-ctags-into
  [ctags-path seed-coll builder-fn]
  ;; http://ctags.sourceforge.net/FORMAT
  (let [ctags-row-re #"(?x)     # free-form
                       ^        # start with
                       ([^\t]+) # tagname
                       \t       # separated by a tab
                       ([^\t]+) # then tagfile
                       \t       # separated by another tab
                       ([^;]+)  # then tagaddress
                       (;\".*)? # possibly tagfield
                       $        # and nothing else"]
    (with-open [r (cji/reader ctags-path)]
      ;; XXX: line-seq is not going to work for etags
      (let [row-strs (line-seq r)]
        (loop [[row-str & row-strs] row-strs
               parsed seed-coll]
          (if row-str
            (let [[_ t-name t-file t-addr t-field]
                  (re-find ctags-row-re row-str)]
              (recur row-strs
                (if (and t-name
                      (not (clojure.string/starts-with? t-name "!_TAG")))
                  ;; there are also lines like:
                  ;;
                  ;;   !_TAG_FILE_FORMAT  {version-number}  /optional comment/
                  ;;
                  ;; skip them for now
                  (builder-fn {:parsed parsed
                               :t-file t-file
                               :t-name t-name
                               :t-addr t-addr})
                  parsed)))
            parsed))))))

(defn read-ctags-into-nested-map
  [ctags-path]
  (read-ctags-into ctags-path {}
    (fn [{:keys [:parsed :t-addr :t-file :t-name]}]
      (let [name-map (get parsed t-file {})]
        (assoc parsed
          t-file (assoc name-map
                   (symbol t-name)
                   (Integer/parseInt t-addr)))))))

(defn read-ctags-into-vector-of-maps
  [ctags-path]
  (read-ctags-into ctags-path []
    (fn [{:keys [:parsed :t-addr :t-file :t-name]}]
      (conj parsed {:file-path t-file
                    :identifier (symbol t-name)
                    :row (Integer/parseInt t-addr)}))))

(defn read-ctags-into-set-of-maps
  [ctags-path]
  (read-ctags-into ctags-path #{}
    (fn [{:keys [:parsed :t-addr :t-file :t-name]}]
      (conj parsed {:file-path t-file
                    :identifier (symbol t-name)
                    :row (Integer/parseInt t-addr)}))))

(comment

  (read-ctags-into-nested-map
    (aiif/path-join (System/getenv "HOME")
      "src/alc.index-defs/tags"))

  (read-ctags-into-vector-of-maps
    (aiif/path-join (System/getenv "HOME")
      "src/alc.index-defs/tags"))

  (read-ctags-into-set-of-maps
    (aiif/path-join (System/getenv "HOME")
      "src/alc.index-defs/tags"))

  )
