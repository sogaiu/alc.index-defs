(ns alc.index-defs.seek)

;; XXX: improvement: detect line ending char(s) and use that to search
;;      robustness: use regex to search for \n, \r, \r\n
(defn seek-to-row
  [string row-no]
  (loop [rows-to-go row-no
         search-from 0]
    (if (= 1 rows-to-go)
      search-from
      (recur (dec rows-to-go)
        (let [search-idx (clojure.string/index-of string
                           "\n" search-from)]
          (assert (>= search-idx 0)
            (str "failed to seek to row in string: " row-no string))
          (inc search-idx))))))

;; XXX: improvement: detect line ending char(s) and use that to search
;;      robustness: use regex to search for \n, \r, \r\n
(defn seek-to-row-col
  [string row-no col-no]
  (let [pos (loop [rows-to-go row-no
                   search-from 0]
              (if (= 1 rows-to-go)
                search-from
                (recur (dec rows-to-go)
                  (let [search-idx (clojure.string/index-of string
                                     "\n" search-from)]
                    (assert (>= search-idx 0)
                      (str "failed to seek to row, col in string: "
                        row-no col-no string))
                    (inc search-idx)))))]
    (+ pos (dec col-no))))

(comment
  
  (def sample-src
    "(ns hi.core)

(defn hi-fn []
  (+ 1 1))

(def my-con 3)

(def ^:export friend
  (+ my-con
    8))")

  (->> (seek-to-row sample-src 1)
    (subs sample-src)
    println)

  (->> (seek-to-row sample-src 3)
    (subs sample-src)
    println)

  (->> (seek-to-row sample-src 6)
    (subs sample-src)
    println)

  (->> (seek-to-row-col sample-src 1 5)
    (subs sample-src)
    println)

  )
