(ns alc.index-defs.table)

(defn write-tags
  [file-path section]
  (spit file-path
    (.toString section) :append true))

