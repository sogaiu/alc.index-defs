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
          (when (not (.isDirectory e))
            (let [e-path (.getName e)
                  e-file (cji/file e-path)
                  e-dir (.getParent e-file) ; can be nil
                  e-name (.getName e-file)]
              (when (and skip-class
                      (not (re-find #"^.*\.class$" e-path)))
                (let [dest-dir-path (if e-dir
                                      (aif/path-join unjar-dir-path e-dir)
                                      unjar-dir-path)]
                  (when (not (aif/ensure-dir (cji/file dest-dir-path)))
                    (throw (Exception.
                             (str "failed to prepare dest dir for: " e-dir))))
                  (let [dest-file (cji/file
                                    (aif/path-join dest-dir-path e-name))]
                    (when (not dest-file)
                      (throw (Exception.
                               (str "java.io.File creation failed: " e-name))))
                    (cji/copy (.getInputStream z e) dest-file))))))))))))

(comment
  
  (unzip-jar
    (aif/path-join (System/getenv "HOME")
      ".m2/repository/com/wsscode/pathom/2.2.7/pathom-2.2.7.jar")
    "/tmp")
              
  )

(defn unzip-jars
  [{:keys [:ns-defs :unzip-root :var-defs :var-uses]}]
  ;; XXX: redundant to be looking at var-defs?
  (doseq [jar-path (->> (concat ns-defs var-defs var-uses)
                     (keep (fn [{:keys [jar-path]}]
                             jar-path))
                     distinct)]
    (unzip-jar jar-path unzip-root)))
