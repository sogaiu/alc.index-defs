(ns alc.index-defs.ctags
  (:require
   [alc.index-defs.core :as aic]))

(defn -main [& args]
  (let [[front-str & other] args
        front (when front-str
                (read-string front-str))
        opts {:proj-dir (if (string? front)
                          front
                          (System/getProperty "user.dir"))}
        opts (assoc (merge opts
                      (if (map? front)
                        front
                        {}))
               :format :ctags)]
    (aic/main opts))
  (flush)
  (System/exit 0))
