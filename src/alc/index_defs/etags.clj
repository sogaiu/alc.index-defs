(ns alc.index-defs.etags
  (:require
   [alc.index-defs.core :as aic]
   [alc.index-defs.opts :as aio]))

(defn -main [& args]
  (let [[front-str & other-strs] args
        front (when front-str
                (read-string front-str))
        opts {:proj-dir (if (string? front)
                          front
                          (System/getProperty "user.dir"))}
        opts (merge opts
               (if (map? front)
                 front
                 {}))
        opts (merge opts
               (aio/merge-only-map-strs other-strs))
        opts (assoc opts
               :format :etags)]
    (aic/main opts))
  (flush)
  (System/exit 0))
