(ns alc.index-defs.impl.fs
  (:require
   [clojure.string :as cs]))

(def cache
  (atom {}))

(defn reset-cache!
  []
  (reset! cache {}))

(defn get-content
  [path]
  (if-let [cached-str (get @cache path)]
    cached-str
    (let [slurped (slurp path)]
      (swap! cache assoc
        path slurped)
      slurped)))

(defn ensure-dir
  [dir]
  (if (not (.exists dir))
    (.mkdirs dir)
    true))

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

(defn try-relativize
  [path leading-paths]
  (loop [[a-path & the-rest] leading-paths]
    (if (nil? a-path)
      path
      (if (cs/starts-with? path a-path)
        (path-split path a-path)
        (recur the-rest)))))
