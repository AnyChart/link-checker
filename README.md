# link-checker

A Clojure library designed to find broken links in your site.

## Installation

#### Leiningen

[![Clojars Project](https://img.shields.io/clojars/v/com.anychart/link-checker.svg)](https://clojars.org/com.anychart/link-checker)


## Usage


```clojure
(:require [link-checker.core :as link-checher])

(link-checker/start-by-sitemap-url
    "https://docs.anychart.com/sitemap"
    {:check-fn       (fn [url data]
                       (.contains url "//docs.anychart.com"))
     :max-loop-count 100
     :end-fn         (fn [res] (println "RESULT: " res))})
```

## License
[© AnyChart.com - JavaScript charts](http://www.anychart.com).
