(ns alc.index-defs.impl.bin)

(defn append-etags-section-header
  [sb path]
  (doto sb
    (.append \u000c) ; form-feed
    (.append \u000a) ; new-line
    (.append path)
    (.append \u002c) ; comma
    (.append \u000a) ; new-line
    ))

(defn append-etags-row
  [sb {:keys [:hint :identifier :row]}]
  (doto sb
    (.append hint)
    (.append \u007f) ; del
    (.append (str identifier))
    (.append \u0001) ; soh
    (.append (str row ","))
    (.append \u000a) ; new-line
    ))

(defn make-etags-section
  [{:keys [:file-path :entries]}]
  (let [sb (StringBuilder.)]
    ;; content of sb changes
    (append-etags-section-header sb file-path)
    (doseq [{:keys [:hint :identifier :row]} entries]
      (append-etags-row sb
        {:file-path file-path
         :hint hint
         :identifier identifier
         :row row}))
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

  (require '[clojure.java.io :as cji])

  {:file-path (.getPath (cji/file (System/getenv "HOME")
                          "src" "antoine" "renderer.cljs"))
   :entries [{:hint "(ns antoine.renderer"
              :identifier 'antoine.renderer
              :row 1}
             {:hint "(defn start"
              :identifier 'start
              :row 46}
             {:hint "(defn stop"
              :identifier 'stop
              :row 56}
             {:hint "(defn ^:export init"
              :identifier 'init
              :row 64}]}

  )
