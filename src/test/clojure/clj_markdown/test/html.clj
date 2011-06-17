(ns clj-markdown.test.html
  (:use clojure.test clj-markdown.core clojure.contrib.pprint)
  (:require
   [clojure.java.io :as io]
   [clojure.zip :as zip]))

(letfn [
        (to-html-heading [k] (keyword (str \h (last (name k)))))
        (emit-html-for-block [block]
                             (let [root (zip/vector-zip block)
                                   marker (zip/node (zip/next root))]
                               (zip/root
                                (cond
                                 (contains? (apply hash-set (map #(keyword (str 'clj-markdown.core) %) (map #(str "heading" %) (range 1 (inc 6))))) marker)
                                 (zip/replace (zip/next root) (to-html-heading marker))

                                 (= marker :clj-markdown.core/xml)
                                 (zip/edit root second)

                                 :otherwise root))))]

  (pprint (take 3 (map emit-html-for-block
                       (markdown
                        (.getResourceAsStream (class System) "/markdown-tests/Markdown Documentation - Basics.text"))))))



