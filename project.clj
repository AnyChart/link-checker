(defproject com.anychart/link-checker "0.3.0"
  :description "Library for finding broken links"
  :url "https://github.com/AnyChart/link-checker"
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"}
  :main ^:aot link-checker.core
  :plugins [[lein-ancient "0.6.10"]
            [lein-kibit "0.1.3"]]
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.apache.commons/commons-lang3 "3.8.1"]
                 [clj-http "3.9.1"]
                 [com.cemerick/url "0.1.1"]
                 [enlive "1.1.6"]
                 [org.jsoup/jsoup "1.11.3"]])
