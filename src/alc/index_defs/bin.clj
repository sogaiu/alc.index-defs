(ns alc.index-defs.bin)

(defn make-section-header
  [sb path]
  (doto sb
    (.append \u000c) ; form-feed
    (.append \u000a) ; new-line
    (.append path)
    (.append \u002c) ; comma
    (.append \u000a) ; new-line
    ))

(defn make-tag-line
  [sb hint identifier line-no]
  (doto sb
    (.append hint)
    (.append \u007f) ; del
    (.append (str identifier))
    (.append \u0001) ; soh
    (.append (str line-no ","))
    (.append \u000a) ; new-line
    ))

(defn make-section
  [{:keys [:file-path :entries]}]
  (let [sb (StringBuilder.)]
    ;; content of sb changes
    (make-section-header sb file-path)
    (doseq [{:keys [:hint :identifier :line]} entries]
      (make-tag-line sb hint identifier line))
    sb))

;; make-section takes as input something like:
(comment

  {:file-path (str (System/getenv "HOME")
                "src/antoine/renderer.cljs")
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
