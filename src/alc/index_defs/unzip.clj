(ns alc.index-defs.unzip
  (:require
   [alc.index-defs.fs :as aif]
   [clojure.java.io :as cji])
  (:import [java.util.zip ZipFile]))

;; XXX: most likely this could be simpler, e.g.
;;        me.raynes.fs.compression's unzip
(defn unzip-jar
  ([jar-path target-path]
   (unzip-jar jar-path target-path nil))
  ([jar-path target-path {:keys [:skip-class]
                          :or {skip-class true}}]
   (let [jar-name (.getName (java.io.File. jar-path))
        unjar-dir (java.io.File. target-path jar-name)
        unjar-dir-path (.getAbsolutePath unjar-dir)]
    (when (aif/ensure-dir unjar-dir)
      (with-open [z (ZipFile. jar-path)]
        (doseq [e (enumeration-seq (.entries z))]
          (let [e-path (.getName e)
                [_ e-dir e-name] (re-find #"^(.*)/([^/]+)$" e-path)]
            (when e-name ; only process files
              (when (and skip-class
                      (not (re-find #"^.*\.class$" e-path)))
                (let [dest-dir-nio-path (java.nio.file.Paths/get unjar-dir-path
                                          (into-array String
                                            (-> e-dir
                                              (clojure.string/split #"/"))))]
                  (when (not (aif/ensure-dir (.toFile dest-dir-nio-path)))
                    (throw (Exception.
                             (str "failed to prepare dest dir for: " e-dir))))
                  (let [dest-file (.toFile (.resolve dest-dir-nio-path e-name))]
                    (when (not dest-file)
                      (throw (Exception.
                               (str "java.io.File creation failed: " e-name))))
                    (cji/copy (.getInputStream z e) dest-file))))))))))))

(comment
  
  (unzip-jar
    ;; steps toward os-independent path manipulation
    (.toString
      (java.nio.file.Paths/get (System/getenv "HOME")
        (into-array String
          (-> ".m2/repository/com/wsscode/pathom/2.2.7/pathom-2.2.7.jar"
            (clojure.string/split #"/"))))) ; XXX: use platform file path sep?
    "/tmp")
              
  )
