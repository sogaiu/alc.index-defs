(ns alc.index-defs.main
  (:require
   [alc.index-defs.core :as aic]
   [alc.index-defs.impl.opts :as aiio]))

(defn -main
  [& args]
  (let [opts {:proj-dir
              (if-let [first-str-opt (->> args
                                       (keep #(string? (read-string %)))
                                       first)]
                first-str-opt
                (System/getProperty "user.dir"))}
        opts (merge opts
               (aiio/merge-only-map-strs args))]
    (aic/do-it! opts))
  (flush)
  (System/exit 0))
