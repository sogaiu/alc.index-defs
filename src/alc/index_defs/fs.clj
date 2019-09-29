(ns alc.index-defs.fs)

(defn ensure-dir
  [dir]
  (if (not (.exists dir))
    (.mkdirs dir)
    true))
