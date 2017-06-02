# link-checker

A Clojure library designed to find broken links in your site.

## Installation

#### Leiningen

[![Clojars Project](https://img.shields.io/clojars/v/com.anychart/link-checker.svg)](https://clojars.org/com.anychart/link-checker)


## Usage


```clojure
(ns my-app.core
  (:require [link-checker.core :as link-checker]
            [link-checker.utils :as link-checker-utils]))


(link-checker/start-by-sitemap-url
  "https://docs.anychart.com/sitemap"
  {;; function for filtering links which to process
   :check-fn         (fn [url data]
                       (.contains url "//docs.anychart.com"))
   ;; function which will be invoked each itaration to control process
   :iteration-fn     (fn [iteration urls-count remaining-checked-urls-count total-urls-count]
                       (println "Iteration: " iteration urls-count remaining-checked-urls-count total-urls-count))
   ;; each loop it sends 100 requests
   :max-loop-count   50
   ;; apply to urls like '//example.com'
   :default-protocol "https"
   ;; invoke on end
   :end-fn           (fn [result]
                       ;; broken pages
                       (println "RESULT: " result)
                       ;; pages that have links to broken pages
                       (println "REVERTED RESULT: " (link-checker-utils/revert-result result)))})
```

## License
[© AnyChart.com - JavaScript charts](http://www.anychart.com).
