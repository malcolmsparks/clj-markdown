;; Copyright 2011 Malcolm Sparks.
;;
;; This file is part of clj-markdown.
;;
;; clj-markdown is free software: you can redistribute it and/or modify it under the
;; terms of the GNU Affero General Public License as published by the Free
;; Software Foundation, either version 3 of the License, or (at your option) any
;; later version.
;;
;; clj-markdown is distributed in the hope that it will be useful but WITHOUT ANY
;; WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
;; A PARTICULAR PURPOSE.  See the GNU Affero General Public License for more
;; details.
;;
;; Please see the LICENSE file for a copy of the GNU Affero General Public License.

(ns clj-markdown.test.core
  (:use
   clojure.contrib.pprint
   clojure.test
   clj-markdown.core)
  (:require
   [clojure.java.io :as io]))

(deftest test-markdown-elements
  (is (= 276
         (count (markdown
                 (.getResourceAsStream (class System) "/markdown-tests/Markdown Documentation - Syntax.text"))))))

(comment
  (pprint
   (map #(dissoc % :remaining)
        (process-markdown-lines
         (map (fn [lineno line] (assoc line :lineno lineno))
              (map inc (range))         ; 1..infinity
              (read-markdown-lines
               (io/reader
                (.getResourceAsStream (class System) "/markdown-tests/Markdown Documentation - Basics.text"))))))))

(pprint
 (markdown
  (.getResourceAsStream (class System) "/markdown-tests/Markdown Documentation - Basics.text")))

(pprint
 (markdown
  (.getResourceAsStream (class System) "/markdown-tests/Blockquotes with code blocks.text")))
