(defproject com.anychart/link-checker "0.1.0-SNAPSHOT"
  :description "Library for finding broken links"
  :url "https://github.com/AnyChart/link-checker"
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"}
  :main ^:aot link-checker.core
  :plugins [[lein-ancient "0.6.10"]
            [lein-kibit "0.1.3"]]
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.apache.commons/commons-lang3 "3.5"]
                 [clj-http "3.6.0"]
                 [com.cemerick/url "0.1.1"]
                 [enlive "1.1.6"]])
