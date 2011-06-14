(ns clj-markdown.test.official-test
  (:use
   clj-markdown.core
   clojure.test
   clojure.contrib.pprint)
  (:require
   clojure.xml
   [clojure.java.io :as io]
   [clojure.contrib.prxml :as prxml]))

(def tests ["#Hard-wrapped paragraphs with list-like lines"
            "#Inline HTML (Simple)"
            "#Horizontal rules"
            "Markdown Documentation - Basics"
            "#Backslash escapes"
            "#Links, reference style"
            "#Tabs"
            "#Amps and angle encoding"
            "#Auto links"
            "#Literal quotes in titles"
            "#Inline HTML comments"
            "#Links, inline style"
            "#Nested blockquotes"
            "#Markdown Documentation - Syntax"
            "#Inline HTML (Advanced)"
            "#Strong and em together"
            "#Tidyness"
            "#Ordered and unordered lists"
            "#Blockquotes with code blocks"])

(comment
  (deftest test-all
    (doall
     (let [dir (io/file (System/getProperty "user.home") "Downloads/MarkdownTest_1.0/Tests")]
       (for [t tests]
         (when (not (.startsWith t "#"))
           (let [infile (io/file dir (str t ".text"))
                 htmlfile (io/file dir (str t ".html"))]
             (is (true? (.exists infile)))
             (is (= (clojure.xml/parse (io/input-stream (.getBytes (str "<div>" (slurp htmlfile) "</div>"))))
                    (clojure.xml/parse (io/input-stream (.getBytes (with-out-str (prxml/prxml (vec (cons :div (markdown (io/reader infile))))))))))))))))))

