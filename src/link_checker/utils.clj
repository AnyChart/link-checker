(ns link-checker.utils
  (:require [clojure.string :as string]))

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