(ns clj-markdown.test.html
  (:use clojure.test clj-markdown.core clojure.contrib.pprint)
  (:require
   [clojure.java.io :as io]
   [clojure.zip :as zip]
   [clojure.contrib.zip-filter :as zipf]
   [clojure.contrib.prxml :as prxml]))


(defn emit-html-for-block
  [block]
  (letfn [(to-html-heading [k] (keyword (str \h (last (name k)))))]
    (let [root (zip/vector-zip block)
          marker (zip/node (zip/next root))
          head (zip/next root)]
      (zip/root
       (cond
        (contains? (apply hash-set (map #(keyword (str 'clj-markdown.core) %)
                                        (map #(str "heading" %)
                                             (range 1 (inc 6)))))
                   marker)
        (zip/replace head (to-html-heading marker))

        (= marker :clj-markdown.core/xml)
        (zip/edit root second)

        (= marker :clj-markdown.core/para)
        (->
         head
         (zip/replace :p)
         (zip/next)
         (zip/edit #(reduce str (interpose " " %))))

        (= marker :clj-markdown.core/ulist)
        (->
         head
         (zip/replace :ul))

        (= marker :clj-markdown.core/code-block)
        (->
         head
         (zip/replace :pre)
         (zip/next)
         (zip/edit #(reduce str (interpose " " %))))

        :otherwise root)))))

(defn emit-html [blocks]
  (vec (cons :div (map emit-html-for-block blocks))))

(defn print-html [blocks]
  (binding [prxml/*prxml-indent* 4]
    (prxml/prxml (emit-html blocks))))

(comment
  (pprint
   (emit-html
    (markdown
     (.getResourceAsStream (class System) "/markdown-tests/Markdown Documentation - Basics.text"))))

  (print-html
   (markdown
    (.getResourceAsStream (class System) "/markdown-tests/Markdown Documentation - Basics.text")))

  (pprint
   (emit-html
    (markdown (io/file "/home/malcolm/src/clojure-contrib/README.md")))))

