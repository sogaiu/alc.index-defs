(ns alc.index-defs.table
  (:require
   [clojure.java.io :as cji]))

(defn write-tags
  [file-path {:keys [:header :tag-lines]}]
  (with-open [out (cji/output-stream file-path :append true)]
    (.write out header)
    (doseq [tag-line tag-lines]
      (.write out tag-line))))
