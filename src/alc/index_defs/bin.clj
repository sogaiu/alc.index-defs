(ns alc.index-defs.bin)

(defn make-section-header
  [sb path format]
  (cond
    (= format :etags)
    (doto sb
      (.append \u000c) ; form-feed
      (.append \u000a) ; new-line
      (.append path)
      (.append \u002c) ; comma
      (.append \u000a) ; new-line
      )
    ;;
    (= format :ctags)
    sb ; nothing to do
    ;;
    :else
    (throw (Exception. (str "unrecognized format: " format)))))

(defn make-tag-line
  [sb {:keys [:file-path :hint :identifier :line-no]} format]
  (cond
    (= format :etags)
    (doto sb
      (.append hint)
      (.append \u007f) ; del
      (.append (str identifier))
      (.append \u0001) ; soh
      (.append (str line-no ","))
      (.append \u000a) ; new-line
      )
    ;;
    (= format :ctags)
    (doto sb
      (.append (str identifier))
      (.append \u0009) ; tab
      (.append file-path)
      (.append \u0009) ; tab
      ;;(.append (str "/^" hint "/;") ; XXX: hint up to 96 chars?
      (.append (str line-no))
      (.append \u000a) ; new-line
      )
    ;;
    :else
    (throw (Exception. (str "unrecognized format: " format)))))

(defn make-section
  [{:keys [:file-path :format :entries]}]
  (let [sb (StringBuilder.)]
    ;; content of sb changes
    (make-section-header sb file-path format)
    (doseq [{:keys [:hint :identifier :line]} entries]
      (make-tag-line sb
        {:file-path file-path
         :hint hint
         :identifier identifier
         :line-no line}
        format))
    sb))

(defn append-ctags-row
  [sb {:keys [:file-path :identifier :row]}]
  (doto sb
      (.append (str identifier))
      (.append \u0009) ; tab
      (.append file-path)
      (.append \u0009) ; tab
      (.append (str row))
      (.append \u000a) ; new-line
      ))

;; make-section takes as input something like:
(comment

  {:file-path (str (System/getenv "HOME")
                "src/antoine/renderer.cljs")
   :format :etags ; or :ctags
   :entries [{:hint "(ns antoine.renderer"
              :identifier 'antoine.renderer
              :line 1}
             {:hint "(defn start"
              :identifier 'start
              :line 46}
             {:hint "(defn stop"
              :identifier 'stop
              :line 56}
             {:hint "(defn ^:export init"
              :identifier 'init
              :line 64}]}

  )
