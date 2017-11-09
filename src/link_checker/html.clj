(ns link-checker.html
  (:require [net.cgrand.enlive-html :as html]
            [link-checker.url :as url-utils]
            [cemerick.url]
            [clojure.string :as string]
            [link-checker.utils :as utils])
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
        ;; remove canonical link from hrefs
        hrefs-css (filter (fn [link] (not= "canonical" (.attr link "rel"))) hrefs-css)
        hrefs-css (map (fn [link] {:href (.attr link "href")
                                   :text "text/css"}) hrefs-css)

        hrefs-js (.select doc "script[src]")
        hrefs-js (map (fn [link] {:href (.attr link "src")
                                  :text "text/javascript"}) hrefs-js)]
    (concat hrefs hrefs-css hrefs-js)))
;

(defn delete-page-hash [url]
  (first (string/split url #"#")))

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

(defn fix-url [url source-url config]
  (try
    (-> url
       ;; delete hash from last
       ; delete-page-hash
       ;; add https if not
       (add-protocol config)
       ;;.add base to "/path/path"
       (add-base-path (url-utils/base-path source-url))
       ;; fix relative: "../path/path"
       (fix-relative-url source-url)

       url-utils/prepare-url)
    (catch Exception e
      url)))


;; (link-checker.html/get-page-urls "https://docs.anychart.com/Quick_Start/Quick_Start"
;;                                  (slurp "https://docs.anychart.com/Quick_Start/Quick_Start")
;;                                  {:default-protocol "http"})
;; return example
(comment
  [{:url   "http://docs.anychart.stg/Graphics/Hatch_Fill_Settings",
    :links '({:href "/Graphics/Hatch_Fill_Settings", :text "Hatch Fill Settings"})}
   {:url   "https://www.anychart.com",
    :links '({:href "https://www.anychart.com", :text ""}
              {:href "https://www.anychart.com", :text "AnyChart"}
              {:href "https://www.anychart.com", :text "AnyChart.Com"})}])




(defn get-page-urls [source-url s config]
  (let [;base-path (url-utils/base-path source-url)
        source-url (utils/drop-ref source-url)
        raw-urls1 (get-raw-hrefs-soup s)

        ;; delete local #hrefs
        ;; TODO: add self checking for internal links e.g: #page-anchor
        ;raw-urls2 (distinct (remove #(.startsWith (:href %) "#") raw-urls1))

        {raw-urls2 false ref-urls true} (group-by #(.startsWith (:href %) "#") raw-urls1)
        ref-urls (filter #(utils/bad-ref? (:href %) s) ref-urls)
        ref-urls (map (fn [link]
                        {:url   (str source-url (:href link))
                         :links [link]}) ref-urls)

        raw-urls2 (distinct raw-urls2)


        ;; create hash-map data for each url
        raw-urls3 (map #(hash-map :url (fix-url (:href %) source-url config)
                                  :link %) raw-urls2)
        ;; group hrefs to one link
        raw-urls (group-by :url raw-urls3)
        raw-urls (map (fn [[url data]] {:url url :links (map :link data)}) raw-urls)]
    [raw-urls ref-urls]))