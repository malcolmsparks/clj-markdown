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

(ns clj-markdown.core
  (:use clojure.contrib.pprint)
  (:require [clojure.java.io :as io]
            [clojure.zip :as zip]
            [clojure.contrib.zip-filter :as zipf]))

(defprotocol LineProcessor
  (process-line [this state])
  (process-eof [this state]))

(defn process-lines [^LineProcessor proc lines]
  "Call a function for each line with a structure that gives the function access
to both the individual line and the remaining lines. This separates the job of
iterating over the lines from the processing of lines themselves. The
LineProcessor function returns the next state, but this defaults to the next
line, thereby reducing the probability that a mistake in the processor will
cause the process to run infinitely."
  (let [[results eofs]
        (split-with #(not (contains? % :end))
                    (letfn [(step [{[line & rem] :remaining :as state}]
                                  (let [new-state (assoc state :line line :remaining rem)
                                        new-state-tidied (dissoc new-state :yield)]
                                    (if (nil? line) (assoc (process-eof proc new-state-tidied) :end true)
                                        (try
                                          (process-line proc new-state-tidied)
                                          (catch AssertionError e (throw (Exception. (format "Assertion failed, state was %s" (dissoc new-state-tidied :remaining)) e)))
                                          (catch Exception e (throw (Exception. (format "Assertion failed, state was %s" (dissoc new-state-tidied :remaining)) e)))))))]
                      (iterate step {:remaining lines})))]
    (concat results (list (first eofs)))))

(defn filter-yields [states]
  (filter #(not (nil? %)) (map :yield states)))

(defn line? [x]
  (and (map? x)
       (every? #(contains? x %) [:value :leading :trailing :empty])))

(defn read-markdown-lines [reader]
  (letfn [
          (space? [x] (= (int x) 32))
          (get-leading [x] (count (take-while space? x)))
          (get-trailing [x] (count (take-while space? (reverse x))))
          (init [line]
                (assoc {:value line}
                  :leading (get-leading line)
                  :trailing (get-trailing line)
                  :empty (= (get-leading line) (count line))))]
    (map init (line-seq reader))))

(defn parse-xml-open-token [input]
  (letfn [(parse-open-tag [tag] (re-seq #"<(\S+)([^>]*)>" tag))
          (strip-quotes [s] (.substring s 1 (dec (count s))))]
    (let [[_ tag attrs] (first (parse-open-tag input))
          attrs (apply hash-map (mapcat #(let [[[ _ n v] & _]
                                               (re-seq #"\s+([^=\s]+)\s?=\s?(\'[^\']*\'|\"[^\"]*\")" %)]
                                           [(keyword n) (strip-quotes v)])
                                        (re-seq #"\s[^=\s]+\s?=\s?(?:\'[^\']*\'|\"[^\"]*\")" attrs)))]
      (if (empty? attrs)
        [(keyword tag)]
        [(keyword tag) attrs]))))

(defn- fold [stack]
  (cond (empty? stack) nil
        (= (count stack) 1) (first stack)
        :otherwise (conj (pop (pop stack)) (conj (peek (pop stack)) (peek stack)))))

(defn- parse-xml [stack tok]
  (cond
   (.startsWith tok "</") (fold stack)
   (.startsWith tok "<") (conj stack (parse-xml-open-token tok))
   (= 0 (count (.trim tok))) stack
   :otherwise (conj (pop stack) (conj (peek stack) tok))))

(defn- tokenize-by-chevron [s]
  (re-seq #"(?:[<>][^<>]+[<>])|(?:[^<>]+)" s))

(defn replace-patterns-with-markup [s pattern tag]
  (letfn [(wrap [tag s] (format "<%s>%s</%s>" tag s tag))
          (rep [{[[replace with] & remaining] :groups
                 result :result}]
               {:groups remaining :result (.replace result replace (wrap tag with))})]
    (:result (first (drop-while #(not (nil? (:groups %))) (iterate rep {:groups (re-seq pattern s) :result s}))))))

(defn substitute-emphasis
  "Apply markdown standard emphasis"
  [s]
  (-> s
      (replace-patterns-with-markup #"\*\*([\S]+)\*\*" "strong$")
      (replace-patterns-with-markup #"__([\S]+)__" "strong$")
      (replace-patterns-with-markup #"\*([\S]+)\*" "emphasis$")
      (replace-patterns-with-markup #"_([\S]+)_" "emphasis$")))

(defn markup-text [k s]
  (->
   (->> s
        (substitute-emphasis)
        (format "<wrapper>%s</wrapper>")
        tokenize-by-chevron
        (reduce parse-xml '())
        zip/vector-zip
        zip/next
        ((fn [loc] (zip/replace loc k))) ; replace keyword
        (iterate (fn [loc]
                   (cond
                    (zip/end? loc) nil
                    (= (zip/node loc) :strong$) (zip/replace loc ::strong)
                    (= (zip/node loc) :emphasis$) (zip/replace loc ::emphasis)
                    :otherwise (zip/next loc))))
        (take-while #(not (nil? %)))
        last
        zip/root)))

(defn process-markdown-lines [input]
  (letfn [(fold-list-temp [temp]
                          (conj (pop (pop temp))
                                (assoc (peek (pop temp)) :content (conj (:content (peek (pop temp)))
                                                                        (:content (peek temp))))))
          (get-trimmed-value [left-trim line]
                             (let [to (- (count (:value line)) (:trailing line))]
                               (if (<= left-trim to)
                                 (.substring (:value line) left-trim to)
                                 "")))

          (pushback [state] (assoc state :remaining (cons (:line state) (:remaining state))))
          (wrap-xml [v] [::xml v])
          (wrap-ulist [v] [::ulist v])
          (wrap-li [v] [::list-item v])

          (xml-mode [{:keys [line temp] :or {temp []} :as state}]
                    (let [toks (re-seq #"(?:[<>][^<>]+[<>])|(?:[^<>]+)" (:value line))
                          xml (reduce parse-xml temp toks)]
                      (if (vector? xml)
                        (assoc state
                          :case :finish-xml
                          :mode ::text
                          :yield (wrap-xml xml)
                          :temp [])
                        (assoc state
                          :case :continue-xml
                          :temp xml))))

          (list-mode [{:keys [line temp] :or {temp []} :as state}]
                     (cond
                      (true? (:empty line))
                      (assoc state :case ::continue-ulist)

                      (re-matches #"\s*[\*\-\+]\s.*" (:value line))
                      (cond
                       (> (:leading line) (:leading (last temp)))
                       (assoc state
                         :case :continue-ulist
                         :temp (conj temp {:leading (:leading line) :content (wrap-ulist (wrap-li (:value line)))}))

                       :otherwise
                       ;; This collapses the list of nested levels to the correct one
                       (let [new-temp (first (drop-while #(> (:leading (last %)) (:leading line)) (iterate fold-list-temp temp)))]
                         (assoc state
                           :temp (conj (pop new-temp) (assoc (peek new-temp) :yield (:content (peek new-temp)))))))

                      ;; TODO: Revisit list implementation, check dingus - cope
                      ;; with lists at Syntax:591
                      (> (:leading line) 0)
                      (assoc state
                        :temp (conj (pop temp) (assoc (peek temp) :content (conj (:content (peek temp)) (:value line)))))

                      :otherwise
                      (-> state
                          pushback
                          (assoc
                              :mode ::text
                              :case ::ending-ulist
                              :temp []
                              :yield (:content (first
                                                (first
                                                 (drop-while #(> (count %) 1)
                                                             (iterate fold-list-temp temp)))))))))

          (block-quote-mode [{:keys [line temp] :or {temp []} :as state}]
                            (if (:empty line)
                              (assoc state
                                :case ::end-block-quote
                                :yield [::block-quote (map :value temp)]
                                :temp []
                                :mode :text)

                              (assoc state
                                :case ::continue-block-quote
                                :temp (conj temp line))))

          (code-block-mode [{:keys [line temp] :or {temp []} :as state}]
                           (if (>= (:leading line) (:initial-leading state))
                             (assoc state
                               :case ::continue-code-block
                               :temp (conj temp line))

                             (-> (pushback state)
                                 (assoc
                                     :case ::end-code-block
                                     :yield [::code-block (map #(get-trimmed-value (:initial-leading state) %) temp)]
                                     :temp []
                                     :mode ::text)
                                 (dissoc :initial-leading))))]

    (process-lines
     (reify LineProcessor

            (process-eof [this state]
                         (process-line this (assoc state :line {:empty true :leading 0 :trailing 0 :value "" :dummy :end})))

            (process-line
             [this {:keys [line remaining temp mode] :or {temp []} :as state}]

             (cond

              ;; Initialization
              (not (contains? state :temp)) (assoc (pushback state) :case :init :temp [])
              (not (contains? state :mode)) (assoc (pushback state) :case :init :mode ::text)

              ;; Delegate to mode handlers
              (= mode ::xml) (xml-mode state)
              (= mode ::list) (list-mode state)
              (= mode ::block-quote) (block-quote-mode state)
              (= mode ::code-block) (code-block-mode state)

              ;; Heading1
              (and (= (count temp) 1)
                   (re-matches #"[\=]+" (:value line)))
              (assoc state
                :case ::finish-heading1
                :yield (markup-text ::heading1 (:value (first temp))) :temp [])

              ;; Heading2
              (and (= (count temp) 1)
                   (re-matches #"[-]+" (:value line)))
              (assoc state
                :case ::finish-heading2
                :yield (markup-text ::heading2 (:value (first temp)))
                :temp [])

              ;; Block quotes
              (re-matches #">.*" (:value line))
              (do
                (assert (empty? temp))
                (assoc state
                  :case ::start-block-quote
                  :temp [line]
                  :mode ::block-quote))

              ;; Heading (atx style)
              (re-matches #"#{1,6}\s*.*" (:value line))
              (if (empty? temp)
                (let [[_ hashes v] (first (re-seq #"(#{1,6})\s*(.*?)\s*#*$" (:value line)))]
                  (assoc state :yield
                         (case (count hashes)
                               1 [::heading1 v]
                               2 [::heading2 v]
                               3 [::heading3 v]
                               4 [::heading4 v]
                               5 [::heading5 v]
                               6 [::heading6 v])))
                (throw (Exception. (str "Pushback a blank line to ensure the existing temp is yielded properly." line))))

              ;; Horizontal rule
              (re-matches #"(?:-(?:\s*-){2,})|(?:\*(?:\s*\*){2,})|(?:_(?:\s*_){2,})" (:value line))
              (do
                (assert (empty? temp))
                (assoc state
                  :case ::horizontal-rule
                  :yield [::horizontal-rule]))

              ;; Starting a list?
              (re-matches #"\s{0,3}[\*\-\+]\s.*" (:value line))
              (do
                (assert (empty? temp))
                (assoc state
                  :case ::starting-ulist
                  :temp [{:leading (:leading line) :content (wrap-ulist (wrap-li (:value line)))}]
                  :mode ::list))

              ;; Start of an XML block?
              (re-matches #"^<\S+[^>]*>" (:value line))
              (let [_ (assert (empty? temp))
                    toks (re-seq #"(?:[<>][^<>]+[<>])|(?:[^<>]+)" (:value line))
                    xml (reduce parse-xml '() toks)]
                (if (vector? xml)
                  (assoc state :case ::xml-line :yield (wrap-xml xml))
                  (assoc state :case ::start-xml :temp xml :mode ::xml)))

              ;; Start code block
              (>= (:leading line) 4)
              (do
                (assert (empty? temp))
                (assoc state
                  :case ::start-code-block
                  :mode ::code-block
                  :initial-leading (:leading line)
                  :temp (conj temp line)))

              ;; Hard line break
              (re-matches #"\S.*\s{2,}$" (:value line))
              (assoc state
                :case ::hard-line
                :temp (-> temp (conj line) (conj [:br])))

              ;; Line is empty (or contains only whitespace)
              (or
               (:empty line)
               (re-matches #"\s*" (:value line)))
              (if (empty? temp)
                (assoc state :case ::line-empty)
                (assoc state :case ::para
                       :yield (markup-text ::para (reduce str (interpose " " (map #(if (line? %) (:value %) %) temp))))
                       :temp []))

              ;; Otherwise
              :otherwise
              (assoc state :case ::default :temp (conj temp line)))))
     input)))

(defn markdown-debug [input]
  (map #(dissoc % :remaining)
       (process-markdown-lines
        (map (fn [lineno line] (assoc line :lineno lineno))
             (map inc (range))          ; 1..infinity
             (read-markdown-lines (io/reader
                                   input))))))

(defn markdown [input]
  (filter-yields (markdown-debug input)))


