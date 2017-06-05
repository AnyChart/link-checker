(ns link-checker.html
  (:require [net.cgrand.enlive-html :as html]
            [link-checker.url :as url-utils]
            [cemerick.url]
            [clojure.string :as string])
  (:import (org.jsoup Jsoup)))


(defn get-raw-hrefs-soup
  "get href and text from links in page, e. g:
  /beta/Resource_Chart
  https://twitter.com/AnyChart
  ./Downloading_AnyChart
  ../Common_Settings/UI_Controls/AnyChart_UI
  //playground.anychart.com/docs/7.13.1/samples/quick_start_pie-plain
  #someurl
  ... etc"
  [s]
  (let [doc (Jsoup/parse s)
        hrefs (.select doc "a[href]")
        hrefs (map (fn [link] {:href (.attr link "href")
                               :text (.text link)}) hrefs)
        hrefs-css (.select doc "link[href]")
        hrefs-css (map (fn [link] {:href (.attr link "href")
                                   :text "text/css"}) hrefs-css)
        hrefs-js (.select doc "script[src]")
        hrefs-js (map (fn [link] {:href (.attr link "src")
                                  :text "text/javascript"}) hrefs-js)]
    (concat hrefs hrefs-css hrefs-js)))
;

(defn add-protocol [url config]
  (if (.startsWith url "//")
    (str (:default-protocol config) ":" url)
    url))


(defn add-base-path [url base]
  (if (and (.startsWith url "/")
           (not (.startsWith url "//")))
    (str base url)
    url))


(defn fix-relative-url [url source-url]
  (if (or (.startsWith url ".")
          (not (.startsWith url "http")))
    (let [source-url-without-last-part (string/join "/" (butlast (string/split source-url #"/")))]
      (str (cemerick.url/url source-url-without-last-part url)))
    url))


(defn delete-page-hash [url]
  (first (string/split url #"#")))


(defn fix-url [url source-url config]
  (-> url
      ;; delete hash from last
      delete-page-hash
      ;; add https if not
      (add-protocol config)
      ;;.add base to "/path/path"
      (add-base-path (url-utils/base-path source-url))
      ;; fix relative: "../path/path"
      (fix-relative-url source-url)
      url-utils/prepare-url))


;; (link-cheker.html/get-page-urls "https://docs.anychart.com/Quick_Start/Quick_Start"
;;                                  (slurp "https://docs.anychart.com/Quick_Start/Quick_Start")
;;                                  {:default-protocol "http"})
(defn get-page-urls [source-url s config]
  (let [;base-path (url-utils/base-path source-url)
        raw-urls (get-raw-hrefs-soup s)
        ;; delete local #hrefs
        raw-urls (distinct (remove #(.startsWith (:href %) "#") raw-urls))
        ;; create hash-map data for each url
        raw-urls (map #(hash-map :url (fix-url (:href %) source-url config)
                                 :link %) raw-urls)
        ;; group hrefs to one link
        raw-urls (group-by :url raw-urls)
        raw-urls (map (fn [[url data]] {:url url :links (map :link data)}) raw-urls)]
    raw-urls))