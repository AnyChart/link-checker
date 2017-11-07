(ns link-checker.utils
  (:require [clojure.string :as string]))

(def regex-char-esc-smap
  (let [esc-chars "()&^%$#!?*."]
    (zipmap esc-chars
            (map #(str "\\" %) esc-chars))))

(defn str-to-pattern
  [string]
  (->> string
       (replace regex-char-esc-smap)
       (reduce str)))

(defn good-ref? [ref html]
  (let [ref (if (string/starts-with? ref "#")
              (subs ref 1)
              ref)
        ref (str-to-pattern ref)]
    (re-find (re-pattern (str "id=['\"]" ref "['\"]|name=['\"]" ref "['\"]")) html)))

(defn bad-ref? [ref html]
  (not (good-ref? ref html)))


(defn drop-ref [url]
  (first (string/split url #"#")))

(defn revert-result [broken-links]
  (let [
        ;; reverting
        broken-links (mapcat (fn [link]
                               (map (fn [from-link]
                                      (assoc from-link :bad-url (:url link)))
                                    (:from link)))
                             broken-links)
        ;; delete refs from urls: http://domain/path#ref -> http://domain/path
        broken-links (map (fn [link]
                            (update link :url drop-ref))
                          broken-links)]
    (distinct broken-links)))