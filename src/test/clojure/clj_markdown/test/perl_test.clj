(ns clj-markdown.test.perl-test
  (:use clojure.contrib.pprint)
  (:require [clojure.java.shell :as sh]
            [clojure.java.io :as io]
            [clojure.xml :as xml]))

(defn wrap [coll head tail]
  (concat (list head) coll (list tail)))


(def markdown-home (io/file (System/getProperty "user.home") "src/markdown/MarkdownTest_1.0"))

(defn get-official-result [test]
  (letfn [(convert-map [{tag :tag attrs :attrs content :content}] (vec (concat (if (nil? attrs) [tag] [tag attrs]) (map s content))))
          (s [x] (cond (map? x) (convert-map x) :otherwise x))]
    (s (xml/parse (io/input-stream (.getBytes (reduce str (-> (sh/sh
                                                               (.getAbsolutePath (io/file markdown-home "Markdown.pl"))
                                                               (.getAbsolutePath (io/file markdown-home (str "Tests/" test))))
                                                              :out .toCharArray io/reader line-seq (wrap "<div>" "</div>")))))))))



(get-official-result "Auto links.text")


(comment
  (io/input-stream (.getBytes (str "<div>" (slurp htmlfile) "</div>")))

  (xml/parse (io/file markdown-home "Tests/Auto links.html"))

  (-> "ls" sh/sh :out .toCharArray io/reader line-seq))



