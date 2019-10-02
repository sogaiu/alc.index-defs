(ns alc.index-defs.fs)

(defn ensure-dir
  [dir]
  (if (not (.exists dir))
    (.mkdirs dir)
    true))

(defn path-join
  [root-path item-path]
  (let [nio-path (java.nio.file.Paths/get root-path
                   (into-array String []))]
    (-> nio-path
      (.resolve item-path)
      .toString)))
