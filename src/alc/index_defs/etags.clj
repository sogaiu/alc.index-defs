(ns alc.index-defs.etags
  (:require
   [alc.index-defs.core :as aic]
   [alc.index-defs.impl.opts :as aii.o]))

(defn -main [& args]
  (let [opts {:proj-dir
              (if-let [first-str-opt (->> args
                                       (keep #(string? (read-string %)))
                                       first)]
                first-str-opt
                (System/getProperty "user.dir"))}
        opts (merge opts
               (aii.o/merge-only-map-strs args))
        opts (assoc opts
               :format :etags)]
    (aic/do-it! opts))
  (flush)
  (System/exit 0))
