;; Copyright 2010 Malcolm Sparks.
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
  (:require [clojure.java.io :as io]))

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
  (let [[yields eofs]
        (split-with #(not (contains? % :end))
                    (letfn [(step [{[line & rem] :remaining :as state}]
                                  (let [new-state (assoc state :line line :remaining rem)
                                        new-state-tidied (dissoc new-state :yield)]
                                    (if (nil? line) (assoc (process-eof proc new-state-tidied) :end true)
                                        (process-line proc new-state-tidied))))]
                      (iterate step {:remaining lines})))]
    (concat yields (list (first eofs)))))

(defn filter-yields [states]
  (filter #(not (nil? %)) (map :yield states)))

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





(defn process-markdown-lines [input]
  (letfn [(fold [stack]
                (cond (empty? stack) nil
                      (= (count stack) 1) (first stack)
                      :otherwise (conj (pop (pop stack)) (conj (peek (pop stack)) (peek stack)))))
          (fold-list-temp [temp]
                          (conj (pop (pop temp))
                                (assoc (peek (pop temp)) :content (conj (:content (peek (pop temp)))
                                                                        (:content (peek temp))))))
          (get-trimmed-value [left-trim line]
                             (let [to (- (count (:value line)) (:trailing line))]
                               (if (<= left-trim to)
                                 (.substring (:value line) left-trim to)
                                 "")))
          (parse-xml [stack tok]
                     (cond
                      (.startsWith tok "</") (fold stack)
                      (.startsWith tok "<") (conj stack (parse-xml-open-token tok))
                      (= 0 (count (.trim tok))) stack
                      :otherwise (conj (pop stack) (conj (peek stack) tok))))
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

                      (not (nil? (re-matches #"\s*[\*\-\+]\s.*" (:value line))))
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

                      :otherwise
                      (-> state
                          pushback
                          (assoc
                              :mode ::text
                              :case ::ending-ulist
                              :yield (:content (first
                                                (first
                                                 (drop-while #(> (count %) 1)
                                                             (iterate fold-list-temp temp)))))))))

          (code-block-mode [{:keys [line temp] :or {temp []} :as state}]
                           (if (>= (:leading line) (:initial-leading state))
                             (assoc state
                               :case ::continue-code-block
                               :temp (conj temp line))

                             (assoc (pushback state)
                               :case ::end-code-block
                               :yield [::code-block (map #(get-trimmed-value (:initial-leading state) %) temp)]
                               :temp []
                               :mode ::text)))]

    (process-lines
     (reify LineProcessor
            (process-eof [this state]
                         (process-line this (assoc state :line {:empty true :leading 0 :trailing 0 :value ""})))
            (process-line
             [this {:keys [line remaining temp mode] :or {temp []} :as state}]

             (cond

              ;; Initialization
              (not (contains? state :temp)) (assoc (pushback state) :case :init :temp [])
              (not (contains? state :mode)) (assoc (pushback state) :case :init :mode ::text)

              ;; Delegate to mode handlers
              (= mode ::xml) (xml-mode state)
              (= mode ::list) (list-mode state)
              (= mode ::code-block) (code-block-mode state)

              ;; Heading1
              (and (= (count temp) 1)
                   (not (nil? (re-matches #"[\=]+" (:value line)))))
              (assoc state
                :case ::finish-heading1
                :yield [::heading1 (:value (first temp))] :temp [])

              ;; Heading2
              (and (= (count temp) 1)
                   (not (nil? (re-matches #"[-]+" (:value line)))))
              (assoc state
                :case ::finish-heading2
                :yield [::heading2 (:value (first temp))]
                :temp [])

              ;; Heading (atx style)
              (not (nil? (re-matches #"#{1,6}\s*.*" (:value line))))
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
                (throw (Exception. "Pushback a blank line to ensure the existing temp is yielded properly.")))

              ;; Horizontal rule
              (re-matches #"(?:-(?:\s*-){2,})|(?:\*(?:\s*\*){2,})|(?:_(?:\s*_){2,})" (:value line))
              (let [_ (assert (empty? temp))]
                ;; TODO: Do this assert consistently in the algo.
                (assoc state
                  :case ::horizontal-rule
                  :yield [::horizontal-rule]))

              ;; Starting a list?
              (and (empty? temp)
                   (not (nil? (re-matches #"\s{0,3}[\*\-\+]\s.*" (:value line)))))
              (assoc state
                :case ::starting-ulist
                :temp [{:leading (:leading line) :content (wrap-ulist (wrap-li (:value line)))}]
                :mode ::list)

              ;; Start of an XML block?
              (and (empty? temp)
                   (not (empty? (re-seq #"^<\S+[^>]*>" (:value line)))))
              (let [toks (re-seq #"(?:[<>][^<>]+[<>])|(?:[^<>]+)" (:value line))
                    xml (reduce parse-xml '() toks)]
                (if (vector? xml)
                  (assoc state :case ::xml-line :yield (wrap-xml xml))
                  (assoc state :case ::start-xml :temp xml :mode ::xml)))

              ;; Start code block
              (and (empty? temp)
                   (not (:empty line))
                   (>= (:leading line) 4))
              (assoc state
                :case ::start-code-block
                :mode ::code-block
                :initial-leading (:leading line)
                :temp (conj temp line))

              ;; Hard line break
              (re-matches #"\S.*\s{2,}$" (:value line))
              (assoc state
                :case ::para
                     :yield [::para (reduce str (interpose " " (map :value temp)))]
                       :temp [])
              
              ;; Line is empty
              (:empty line)
              (if (empty? temp)
                (assoc state :case ::line-empty)
                (assoc state :case ::para
                       :yield [::para (reduce str (interpose " " (map :value temp)))]
                       :temp []))

              ;; Default paragraph
              :otherwise
              (assoc state :case ::default :temp (conj temp line)))))
     input)))

(defn markdown [input]
  (filter-yields
   (process-markdown-lines
    (map (fn [lineno line] (assoc line :lineno lineno))
         (map inc (range))              ; 1..infinity
         (read-markdown-lines (io/reader
                               input))))))


