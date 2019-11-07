(ns alc.index-defs.impl.tags
  (:require
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

