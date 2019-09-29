(ns alc.index-defs.etags
  (:require
   [alc.index-defs.core :as aic]))

(defn -main [& args]
  (let [[front-str & other] args
        front (when front-str
                (read-string front-str))
        omap {:proj-root (if (string? front)
                           front
                           (System/getProperty "user.dir"))}
        omap (assoc omap
               :opts (if (map? front)
                       front
                       {}))]
    (aic/main (:proj-root omap)
      (:opts omap)))
  (flush)
  (System/exit 0))
