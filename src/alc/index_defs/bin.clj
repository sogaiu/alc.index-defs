(ns alc.index-defs.bin
  (:import [java.nio ByteBuffer]))

;; thanks clojure cookbook :)
(defn make-section-header
  [path]
  (let [form-feed 12 ; 0x0c
        new-line 10 ; 0x0a
        buf-len (+ 1
                  1
                  (count path) 1
                  1)
        bb (ByteBuffer/allocate buf-len)
        buf (byte-array buf-len)]
    (doto bb
      (.put (.byteValue form-feed))
      (.put (.byteValue new-line))
      (.put (.getBytes (str path ",")))
      (.put (.byteValue new-line))
      (.flip)
      (.get buf))
    buf))

(defn make-tag-line
  [hint identifier line-no]
  (let [id-str (str identifier)
        line-no-str (str line-no)
        new-line 10 ; 0x0a
        del 127 ; 0x7f
        soh 1 ; 0x01
        buf-len (+ (count hint)
                  1
                  (count id-str)
                  1
                  (count line-no-str) 1
                  1)
        bb (ByteBuffer/allocate buf-len)
        buf (byte-array buf-len)]
    (doto bb
      (.put (.getBytes hint))
      (.put (.byteValue del))
      (.put (.getBytes id-str))
      (.put (.byteValue soh))
      (.put (.getBytes (str line-no-str ",")))
      (.put (.byteValue new-line))
      (.flip)
      (.get buf))
    buf))

(defn make-section
  [{:keys [:file-path :entries]}]
  (let [header (make-section-header file-path)
        tag-lines (map (fn [{:keys [:hint :identifier :line]}]
                         (make-tag-line hint identifier line))
                    entries)]
    {:header header
     :tag-lines tag-lines}))

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
