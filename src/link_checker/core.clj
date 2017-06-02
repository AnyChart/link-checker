(ns link-checker.core
  (:require [clojure.xml :as xml]
            [clojure.string :as string]
            [clojure.set]
            [clj-http.client :as client]
            [link-checker.utils]
            [link-checker.url]
            [link-checker.html]))


(defn take-n [n hash]
  (into {} (take n hash)))


(defn urls-from-sitemap [url]
  (let [data (xml/parse url)
        urls (map #(->> %
                        :content
                        (filter (fn [x] (= (:tag x) :loc)))
                        first
                        :content
                        first
                        string/trim)
                  (:content data))]
    urls))


(defn check-url-async [url
                       *result
                       urls-count
                       *current-count
                       end-fn
                       config]
  (let [check-end-fn #(when (= urls-count %)
                       (end-fn *result config))]
    (try
      (client/get url {:async? true}
                  (fn [{:keys [body status trace-redirects] :as response}]
                    ;(prn :ok url status @*current-count "/" urls-count)
                    (let [redirected-url (or (last trace-redirects) url)
                          page-urls (link-checker.html/get-page-urls redirected-url body config)]
                      (swap! *result (fn [result]
                                       (let [result (assoc-in result [url :status] status)
                                             result (reduce (fn [result {new-url :url links :links}]
                                                              (update-in result [new-url :from] conj {:url   url
                                                                                                      :links links}))
                                                            result
                                                            page-urls)]
                                         result))))
                    (check-end-fn (swap! *current-count inc)))
                  (fn [exception]
                    ;(prn :error url @*current-count)
                    (swap! *result (fn [result]
                                     (-> result
                                         (assoc-in [url :status] -1)
                                         (assoc-in [url :e] exception))))
                    (check-end-fn (swap! *current-count inc))))
      (catch Exception e
        ;(prn :error-during-start-req url @*current-count)
        (swap! *result (fn [result]
                         (-> result
                             (assoc-in [url :status] -1)
                             (assoc-in [url :e] e))))
        (check-end-fn (swap! *current-count inc))))))


(defn get-urls-for-check [*result check-fn]
  (filter
    (fn [[url data]]
      (and
        (nil? (:status data))
        (check-fn url data)))
    @*result))


(defn run-requests [*result config]
  (swap! (:*loop-count config) inc)
  (let [urls-for-check-total (get-urls-for-check *result (:check-fn config))
        urls-for-check-total-count (count urls-for-check-total)
        urls-for-check (take 100 urls-for-check-total)
        urls-count (count urls-for-check)
        *current-count (atom 0)]
    (if (and (pos? urls-count)
             (<= @(:*loop-count config) (:max-loop-count config)))
      (do
        ((:iteration-fn config) @(:*loop-count config) urls-count urls-for-check-total-count (count @*result))
        (doseq [[url _] urls-for-check]
          (check-url-async url *result urls-count *current-count run-requests config)))
      (do
        (let [report-result-filtered (filter
                                       (fn [[url data]]
                                         (and
                                           ((:check-fn config) url data)
                                           (integer? (:status data))
                                           (not= (:status data) 200)))
                                       @*result)
              report-result (map (fn [[url data]] {:url  url
                                                   :from (:from data)}) report-result-filtered)]
          ;(println "Print report, urls for check: " (count (get-urls-for-check *result (:check-fn config))) ", total: " (count @*result))
          (when (:end-fn config)
            ((:end-fn config) report-result)))))))


(defn start-by-urls [urls init-url config]
  (let [config (assoc config
                 :*loop-count (atom 0)
                 :iteration-fn (or (:iteration-fn config) (constantly true))
                 :max-loop-count (or (:max-loop-count config) Integer/MAX_VALUE)
                 :default-protocol (or (:default-protocol config) "https"))
        result (reduce (fn [res url]
                         (assoc res url {:from [{:url   init-url
                                                 :links [{:href init-url
                                                          :text init-url}]}]}))
                       {}
                       urls)
        *result (atom result)]
    (run-requests *result config)))


(defn start-by-sitemap-url [sitemap-url config]
  (let [sitemap-urls (map link-checker.url/prepare-url (urls-from-sitemap sitemap-url))]
    (start-by-urls sitemap-urls sitemap-url config)))


;; *result struct
(comment
  {"https://docs.anychart.com/Quick_Start/Quick_Start" {:status 200
                                                        :from   [{:url   "https://docs.anychart.com/sitemap"
                                                                  :links [{:href "https://docs.anychart.com/sitemap"
                                                                           :text "sitemap"}]}]}
   "https://docs.anychart.com/Quick_Start/Credits"     {:from [{:url   url
                                                                :links [{:href "https://docs.anychart.com/sitemap"
                                                                         :text "sitemap"}]}]}})
