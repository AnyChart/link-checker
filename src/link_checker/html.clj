(ns link-checker.html
  (:require [net.cgrand.enlive-html :as html]
            [link-checker.url :as url-utils]
            [cemerick.url]
            [clojure.string :as string])
  (:import (org.jsoup Jsoup)))


(defn get-raw-hrefs
  "get href attributes from page, e. g:
  /beta/Resource_Chart
  https://twitter.com/AnyChart
  ./Downloading_AnyChart
  ../Common_Settings/UI_Controls/AnyChart_UI
  //playground.anychart.com/docs/7.13.1/samples/quick_start_pie-plain
  #someurl
  ... etc"
  [s]
  (let [page (html/html-snippet s)
        hrefs (some->> (html/select page [:a])
                       (filter #(some? (:href (:attrs %))))
                       (map #(:href (:attrs %))))]
    hrefs))


(defn get-raw-hrefs-soup [s]
  (let [doc (Jsoup/parse s)
        hrefs (.select doc "a[href]")
        hrefs (map (fn [link] (.attr link "href"))  hrefs)]
    hrefs))


(defn add-protocol [url]
  (if (.startsWith url "//")
    (str "https:" url)
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


(defn fix-url [url source-url]
  (-> url
      ;; delete hash from last
      delete-page-hash
      ;; add https if not
      add-protocol
      ;;.add base to "/path/path"
      (add-base-path (url-utils/base-path source-url))
      ;; fix relative: "../path/path"
      (fix-relative-url source-url)))


;; (link-cheker.html/get-page-urls "https://docs.anychart.com/Quick_Start/Quick_Start" (slurp "https://docs.anychart.com/Quick_Start/Quick_Start"))
(defn get-page-urls [source-url s]
  (let [                                                    ;base-path (url-utils/base-path source-url)
        raw-urls (get-raw-hrefs-soup s)
        ;; delete local #hrefs
        raw-urls (distinct (remove #(.startsWith % "#") raw-urls))
        ;; create hash-map data for each url
        raw-urls (map #(hash-map :url (fix-url % source-url)
                                 :href %) raw-urls)
        ;; group hrefs to one link
        raw-urls (group-by :url raw-urls)
        raw-urls (map (fn [[url data]] {:url url :hrefs (map :href data)}) raw-urls)]
    raw-urls))