(ns link-checker.utils)

(defn revert-result [broken-links]
  (let [broken-links (mapcat (fn [link]
                               (map (fn [from-link] (assoc from-link :bad-url (:url link)))
                                    (:from link)))
                             broken-links)]
    broken-links))