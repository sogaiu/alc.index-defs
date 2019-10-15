(ns alc.index-defs.massage)

(in-ns 'clj-kondo.impl.analysis)

(defn reg-usage! [{:keys [analysis] :as _ctx}
                  filename row col from-ns to-ns full-fn-name var-name arity
                  lang metadata]
  (let [to-ns (or (some-> to-ns meta :raw-name) to-ns)
        imported-ns (:imported-ns metadata)]
    (swap! analysis update :var-usages conj
           (assoc-some
            (merge
             ;; XXX: keep metadata, but override
             metadata
             {:filename filename
              :row row
              :col col
              :from from-ns
              :to to-ns
              :name var-name})
             ;; (select-some metadata
             ;;              [:private :macro
             ;;               :fixed-arities
             ;;               :var-args-min-arity
             ;;               :deprecated]))
            :full-fn-name full-fn-name
            :arity arity
            :lang lang))))

(defn reg-var! [{:keys [:analysis :base-lang :lang] :as _ctx}
                filename row col ns name attrs]
  ;; XXX: don't remove anything
  (let [attrs attrs #_(select-keys attrs [:private :macro :fixed-arities :varargs-min-arity
                                  :doc :added :deprecated])]
    (swap! analysis update :var-definitions conj
           (assoc-some
            (merge
             ;; XXX: keep metadata, but override
             attrs
             {:filename filename
              :row row
              :col col
              :ns ns
              :name name})
            ;; (merge {:filename filename
            ;;         :row row
            ;;         :col col
            ;;         :ns ns
            ;;         :name name}
            ;;        attrs)
            :lang (when (= :cljc base-lang) lang)))))

(in-ns 'clj-kondo.impl.analyzer)

(defn analyze-call
  [{:keys [:top-level? :base-lang :lang :ns :config] :as ctx}
   {:keys [:arg-count
           :full-fn-name
           :row :col
           :expr]}]
  (let [ns-name (:name ns)
        children (:children expr)
        {resolved-namespace :ns
         resolved-name :name
         unresolved? :unresolved?
         clojure-excluded? :clojure-excluded?
         :as _m}
        (resolve-name ctx ns-name full-fn-name)
        [resolved-as-namespace resolved-as-name _lint-as?]
        (or (when-let
                [[ns n]
                 (config/lint-as config
                                 [resolved-namespace resolved-name])]
              [ns n true])
            [resolved-namespace resolved-name false])
        fq-sym (when (and resolved-namespace
                          resolved-name)
                 (symbol (str resolved-namespace)
                         (str resolved-name)))
        unknown-ns? (= :clj-kondo/unknown-namespace resolved-namespace)
        resolved-namespace* (if unknown-ns?
                              ns-name resolved-namespace)
        ctx (if fq-sym
              (update ctx :callstack
                      (fn [cs]
                        (cons (with-meta [resolved-namespace* resolved-name]
                                (meta expr)) cs)))
              ctx)
        resolved-as-clojure-var-name
        (when (one-of resolved-as-namespace [clojure.core cljs.core])
          resolved-as-name)
        arg-types (if (and resolved-namespace resolved-name
                           (not (linter-disabled? ctx :type-mismatch)))
                    (atom [])
                    nil)
        ctx (assoc ctx :arg-types arg-types)
        ctx (if resolved-as-clojure-var-name
              (assoc ctx
                     :resolved-as-clojure-var-name resolved-as-clojure-var-name)
              ctx)
        analyzed
        (case resolved-as-clojure-var-name
          ns
          (when top-level?
            [(analyze-ns-decl ctx expr)])
          in-ns (if top-level? [(analyze-in-ns ctx expr)]
                    (analyze-children ctx (next children)))
          alias
          [(analyze-alias ctx expr)]
          declare (analyze-declare ctx expr)
          (def defonce defmulti goog-define)
          (do (lint-inline-def! ctx expr)
              (analyze-def ctx expr))
          (defn defn- defmacro definline)
          (do (lint-inline-def! ctx expr)
              (analyze-defn ctx expr))
          defmethod (analyze-defmethod ctx expr)
          defprotocol (analyze-defprotocol ctx expr)
          (defrecord deftype definterface) (analyze-defrecord ctx expr)
          comment
          (analyze-children ctx children)
          (-> some->)
          (analyze-expression** ctx (macroexpand/expand-> ctx expr))
          (->> some->>)
          (analyze-expression** ctx (macroexpand/expand->> ctx expr))
          (. .. proxy extend-protocol doto reify
             defcurried extend-type)
          ;; don't lint calls in these expressions, only register them as used vars
          (analyze-children (-> ctx
                                (ctx-with-linter-disabled :invalid-arity)
                                (ctx-with-linter-disabled :unresolved-symbol)
                                (ctx-with-linter-disabled :type-mismatch))
                            children)
          (cond-> cond->>) (analyze-usages2
                            (-> ctx
                                (ctx-with-linter-disabled :invalid-arity)
                                (ctx-with-linter-disabled :unresolved-symbol)
                                (ctx-with-linter-disabled :type-mismatch)) expr)
          (let let* for doseq dotimes with-open)
          (analyze-like-let ctx expr)
          letfn
          (analyze-letfn ctx expr)
          (if-let if-some when-let when-some when-first)
          (analyze-conditional-let ctx resolved-as-clojure-var-name expr)
          do
          (analyze-do ctx expr)
          (fn fn*)
          (analyze-fn ctx expr)
          case
          (analyze-case ctx expr)
          loop
          (analyze-loop ctx expr)
          recur
          (analyze-recur ctx expr)
          quote nil
          try (analyze-try ctx expr)
          as-> (analyze-as-> ctx expr)
          areduce (analyze-areduce ctx expr)
          this-as (analyze-this-as ctx expr)
          memfn (analyze-memfn ctx expr)
          empty? (analyze-empty? ctx expr)
          (use require)
          (if top-level? (analyze-require ctx expr)
              (analyze-children ctx (next (:children expr))))
          if (analyze-if ctx expr)
          ;; catch-all
          (case [resolved-as-namespace resolved-as-name]
            [schema.core defn]
            (analyze-schema ctx 'defn expr)
            [schema.core defmethod]
            (analyze-schema ctx 'defmethod expr)
            ([clojure.test deftest]
             [cljs.test deftest]
             #_[:clj-kondo/unknown-namespace deftest])
            (do (lint-inline-def! ctx expr)
                (analyze-deftest (assoc ctx :analyze-defn analyze-defn)
                                 resolved-namespace expr))
            [cljs.test async]
            (analyze-cljs-test-async (assoc ctx :analyze-children analyze-children) expr)
            ([clojure.spec.alpha fdef] [cljs.spec.alpha fdef])
            (spec/analyze-fdef (assoc ctx
                                      :analyze-children
                                      analyze-children) expr)
            [potemkin import-vars]
            (potemkin/analyze-import-vars ctx expr)
            ([clojure.core.async alt!] [clojure.core.async alt!!]
             [cljs.core.async alt!] [cljs.core.async alt!!])
            (core-async/analyze-alt! (assoc ctx
                                            :analyze-expression** analyze-expression**
                                            :extract-bindings extract-bindings)
                                     expr)
            ;; catch-all
            (let [next-ctx (cond-> ctx
                             (= '[clojure.core.async thread]
                                [resolved-namespace resolved-name])
                             (assoc-in [:recur-arity :fixed-arity] 0))]
              (analyze-children next-ctx (rest children) false))))]
    (if (= 'ns resolved-as-clojure-var-name)
      analyzed
      (let [in-def (:in-def ctx)
            id (:id expr)
            call (cond-> {:type :call
                          :resolved-ns resolved-namespace
                          :ns ns-name
                          :full-fn-name full-fn-name
                          :name (with-meta
                                  (or resolved-name full-fn-name)
                                  (meta full-fn-name))
                          :unresolved? unresolved?
                          :clojure-excluded? clojure-excluded?
                          :arity arg-count
                          :row row
                          :col col
                          :base-lang base-lang
                          :lang lang
                          :filename (:filename ctx)
                          :expr expr
                          :callstack (:callstack ctx)
                          :config (:config ctx)
                          :top-ns (:top-ns ctx)
                          :arg-types (:arg-types ctx)}
                   id (assoc :id id)
                   in-def (assoc :in-def in-def))]
        (when id (reg-call ctx call id))
        (namespace/reg-var-usage! ctx ns-name call)
        (when-not unresolved?
          (namespace/reg-used-namespace! ctx
                                         ns-name
                                         resolved-namespace))
        (if-let [m (meta analyzed)]
          (with-meta (cons call analyzed)
            m)
          (cons call analyzed))))))

(in-ns 'clj-kondo.impl.linters)

(defn lint-var-usage
  "Lints calls for arity errors, private calls errors. Also dispatches to call-specific linters.
  TODO: split this out in a resolver and a linter, so other linters
  can leverage the resolved results."
  [ctx idacs]
  (let [{:keys [:config]} ctx
        output-analysis? (-> config :output :analysis)
        ;; findings* (:findings ctx)
        findings (for [ns (namespace/list-namespaces ctx)
                       :let [base-lang (:base-lang ns)]
                       call (:used-vars ns)
                       :let [call? (= :call (:type call))
                             unresolved? (:unresolved? call)
                             fn-name (:name call)
                             caller-ns-sym (:ns call)
                             call-lang (:lang call)
                             caller-ns (get-in @(:namespaces ctx)
                                               [base-lang call-lang caller-ns-sym])
                             resolved-ns (:resolved-ns call)
                             refer-alls (:refer-alls caller-ns)
                             called-fn (resolve-call idacs call call-lang
                                                     resolved-ns fn-name unresolved? refer-alls)
                             unresolved-symbol-disabled? (:unresolved-symbol-disabled? call)
                             ;; we can determine if the call was made to another
                             ;; file by looking at the base-lang (in case of
                             ;; CLJS macro imports or the top-level namespace
                             ;; name (in the case of CLJ in-ns)). Looking at the
                             ;; filename proper isn't reliable since that may be
                             ;; <stdin> in clj-kondo.
                             different-file? (or
                                              (not= (:base-lang call) base-lang)
                                              (not= (:top-ns call) (:top-ns called-fn)))
                             row-called-fn (:row called-fn)
                             row-call (:row call)
                             valid-call? (or (not unresolved?)
                                             (when called-fn
                                               (or different-file?
                                                   (not row-called-fn)
                                                   (or (> row-call row-called-fn)
                                                       (and (= row-call row-called-fn)
                                                            (> (:col call) (:col called-fn)))))))
                             _ (when (and (not valid-call?)
                                          (not unresolved-symbol-disabled?))
                                 (namespace/reg-unresolved-symbol! ctx caller-ns-sym fn-name
                                                                   (if call?
                                                                     (merge call (meta fn-name))
                                                                     call)))
                             row (:row call)
                             col (:col call)
                             filename (:filename call)
                             full-fn-name (:full-fn-name call)
                             fn-ns (:ns called-fn)
                             resolved-ns (or fn-ns resolved-ns)
                             arity (:arity call)
                             _ (when output-analysis?
                                 (analysis/reg-usage! ctx
                                                      filename row col caller-ns-sym
                                                      resolved-ns full-fn-name fn-name arity
                                                      (when (= :cljc base-lang)
                                                        call-lang) called-fn))]
                       :when valid-call?
                       :let [fn-name (:name called-fn)
                             _ (when (and unresolved?
                                          (contains? refer-alls
                                                     fn-ns))
                                 (namespace/reg-referred-all-var! (assoc ctx
                                                                         :base-lang base-lang
                                                                         :lang call-lang)
                                                                  caller-ns-sym fn-ns fn-name))
                             arities (:arities called-fn)
                             fixed-arities (or (:fixed-arities called-fn) (into #{} (filter number?) (keys arities)))
                             ;; fixed-arities (:fixed-arities called-fn)
                             var-args-min-arity (or (:var-args-min-arity called-fn) (-> arities :varargs :min-arity))
                             ;; var-args-min-arity (:var-args-min-arity called-fn)
                             ;; _ (prn ">>" (:name called-fn) arities (keys called-fn))
                             arity-error?
                             (and
                              (= :call (:type call))
                              (not (utils/linter-disabled? call :invalid-arity))
                              (or (not-empty fixed-arities)
                                  var-args-min-arity)
                              (not (or (contains? fixed-arities arity)
                                       (and var-args-min-arity (>= arity var-args-min-arity))
                                       (config/skip? config :invalid-arity (rest (:callstack call))))))
                             errors
                             [(when arity-error?
                                {:filename filename
                                 :row row
                                 :col col
                                 :level :error
                                 :type :invalid-arity
                                 :message (arity-error fn-ns fn-name arity fixed-arities var-args-min-arity)})
                              (when (and (:private called-fn)
                                         (not= caller-ns-sym
                                               fn-ns)
                                         (not (:private-access? call))
                                         (not (utils/linter-disabled? call :private-call)))
                                {:filename filename
                                 :row row
                                 :col col
                                 :level :error
                                 :type :private-call
                                 :message (format "#'%s is private"
                                                  (str (:ns called-fn) "/" (:name called-fn)))})
                              (when-let [deprecated (:deprecated called-fn)]
                                (when-not
                                    (or
                                     ;; recursive call
                                     (and
                                      (= fn-ns caller-ns-sym)
                                      (= fn-name (:in-def call)))
                                     (config/deprecated-var-excluded
                                      config
                                      (symbol (str fn-ns)
                                              (str fn-name))
                                      caller-ns-sym (:in-def call)))
                                  {:filename filename
                                   :row row
                                   :col col
                                   :level :error
                                   :type :deprecated-var
                                   :message (str
                                             (format "#'%s is deprecated"
                                                     (str fn-ns "/" fn-name))
                                             (if (true? deprecated)
                                               nil
                                               (str " since " deprecated)))}))]
                             ctx (assoc ctx
                                        :filename filename)
                             _ (when call?
                                 (lint-specific-calls!
                                  (assoc ctx
                                         :filename filename)
                                  call called-fn)
                                 (when-not arity-error?
                                   (lint-arg-types! ctx idacs call called-fn)))]
                       e errors
                       :when e]
                   e)]
    findings))
