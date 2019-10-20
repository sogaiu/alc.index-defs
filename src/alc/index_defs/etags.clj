(ns alc.index-defs.etags
  (:require
   [alc.index-defs.core :as aic]))

(defn -main [& args]
  (let [[front-str & _] args
        front (when front-str
                (read-string front-str))
        opts {:proj-dir (if (string? front)
                          front
                          (System/getProperty "user.dir"))}
        opts (assoc (merge opts
                      (if (map? front)
                        front
                        {}))
               :format :etags)]
    (aic/main opts))
  (flush)
  (System/exit 0))
