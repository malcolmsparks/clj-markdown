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

(ns clj-markdown.test.inline
  (:use
   clojure.contrib.pprint
   clojure.test
   clj-markdown.core)
  (:require
   [clojure.java.io :as io]))


(defn process [s]
  (markdown (java.io.StringReader. s)))



(deftest inline
  (is (= [[:clj-markdown.core/para "Hello World!"]]
           (process "Hello World!")))

  (testing "inline-html"
    (is (= [[:clj-markdown.core/para "Hello " [:span [:em "World!"]]]]
             (process "Hello <span><em>World!</em></span>"))))

  (testing "emphasis-light"
    (let [expected [[:clj-markdown.core/para "Hello " [:clj-markdown.core/emphasis "World!"]]]]
      (is (= expected (process "Hello *World!*")))
      (is (= expected (process "Hello _World!_")))))

  (testing "emphasis-strong"
    (let [expected [[:clj-markdown.core/para "Hello " [:clj-markdown.core/strong "World!"]]]]
      (is (= expected (process "Hello **World!**")))
      (is (= expected (process "Hello __World!__"))))))




