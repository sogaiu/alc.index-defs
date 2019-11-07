(ns alc.index-defs.impl.opts)

(defn merge-only-map-strs
  [map-strs]
  (reduce (fn [acc map-str]
            (let [read-obj (read-string map-str)]
              (if (map? read-obj)
                (merge acc read-obj)
                acc)))
    {}
    map-strs))

(defn check
  [{:keys [:analysis-path :cp-command :format :method :out-name
           :overwrite :paths :proj-dir :verbose]
    :or {format :etags
         overwrite false
         verbose true}
    :as opts}]
  (when verbose
    (println "* checking specified options")
    (println "  input: " opts))
  (let [out-name (cond
                   out-name
                   out-name
                   ;;
                   (= format :ctags)
                   "tags"
                   ;;
                   (= format :etags)
                   "TAGS"
                   ;;
                   :else
                   (throw (Exception.
                            (str "Unrecognized format: " format))))]
  (assert proj-dir
    ":proj-dir is required")
  (assert (not (and analysis-path cp-command method paths))
    "use at most one of :analysis-path, :cp-command, :method, :paths")
  (when cp-command
    (assert (coll? cp-command)
      ":cp-command should be like [\"my-cp-cmd\" \"arg1\" \"arg2\" ...]"))
  (let [opts (assoc opts
               :format format
               :out-name out-name
               :overwrite overwrite
               :verbose verbose)]
    (when verbose
      (println "  output: " opts))
    opts)))
