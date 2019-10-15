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

;; input
(comment

  (path-split "/home/user/src/adorn" "/home/user")

  )

(defn path-split
  [full-path root-path]
  (let [nio-full-path (java.nio.file.Paths/get full-path
                        (into-array String []))
        nio-root-path (java.nio.file.Paths/get root-path
                        (into-array String []))]
    (-> nio-root-path
      (.relativize nio-full-path)
      .toString)))

;; output
(comment

  "src/adorn"

  )
