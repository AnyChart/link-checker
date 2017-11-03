(ns link-checker.core
  (:require [clojure.xml :as xml]
            [clojure.string :as string]
            [clojure.set]
            [clj-http.client :as client]
            [link-checker.utils]
            [link-checker.url]
            [link-checker.html])
  (:import (java.net URL)))

;;======================================================================================================================
;; Utils
;;======================================================================================================================

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


;;======================================================================================================================
;; Requests
;;======================================================================================================================

(defn on-error [url *result urls-count *current-count end-fn config e]
  (swap! *result (fn [result]
                   (-> result
                       (assoc-in [url :status] -1)
                       (assoc-in [url :e] e))))
  (when (= urls-count (swap! *current-count inc))
    (end-fn *result config)))



(defn check-url-async [url
                       *result
                       urls-count
                       *current-count
                       end-fn
                       config]
  (let [check-end-fn #(when (= urls-count %)
                        (end-fn *result config))]
    (try

      (if ((:check-fn config) url)

        (client/get url {:async? true}
                    (fn [{:keys [body status trace-redirects] :as response}]
                      ;(prn :ok url status @*current-count "/" urls-count)
                      ;; full page check, add new pages for checking
                      (let [redirected-url (or (last trace-redirects) url)
                            page-urls (link-checker.html/get-page-urls redirected-url body config)]
                        (swap! *result (fn [result]
                                         (let [result (assoc-in result [url :status]
                                                                (if-let [ref (.getRef (URL. url))]
                                                                  (if (and (= status 200)
                                                                           (re-find (re-pattern (str "id=['\"]" ref "['\"]")) body))
                                                                    status
                                                                    -1)
                                                                  status))
                                               result (reduce (fn [result {new-url :url links :links}]
                                                                (if ((:add-fn config) new-url)
                                                                  (update-in result [new-url :from] conj {:url   url
                                                                                                          :links links})
                                                                  result
                                                                  ))
                                                              result
                                                              page-urls)]
                                           result))))
                      (check-end-fn (swap! *current-count inc)))
                    (fn [e]
                      (on-error url *result urls-count *current-count end-fn config e)))

        (if-let [ref (.getRef (URL. url))]

          (client/get url {:async? true}
                      (fn [{:keys [body status trace-redirects] :as response}]
                        (if (and (= status 200)
                                 (re-find (re-pattern (str "id=['\"]" ref "['\"]")) body))
                          (swap! *result (fn [result]
                                           (assoc-in result [url :status] status)))
                          (swap! *result (fn [result]
                                           (assoc-in result [url :status] -1))))
                        (check-end-fn (swap! *current-count inc)))
                      (fn [e]
                        (on-error url *result urls-count *current-count end-fn config e)))

          (client/head url {:async? true}
                       (fn [{:keys [body status trace-redirects] :as response}]
                         ;(prn :ok url status @*current-count "/" urls-count)
                         (swap! *result (fn [result]
                                          (assoc-in result [url :status] status)))
                         (check-end-fn (swap! *current-count inc)))
                       (fn [e]
                         (on-error url *result urls-count *current-count end-fn config e)))))

      (catch Exception e
        ;(prn :error-during-start-req url @*current-count)
        (on-error url *result urls-count *current-count end-fn config e)))))


;;======================================================================================================================
;; Iteration (bunch requests)
;;======================================================================================================================
(defn get-urls-for-check [*result check-fn]
  (filter
    (fn [[url data]]
      (and
        (nil? (:status data))
        ;(check-fn url data)
        ))
    @*result))


(defn make-iteration? [urls-count config]
  (and (pos? urls-count)
       (<= @(:*loop-count config) (:max-loop-count config))))


(defn iteration-result [*result config]
  (do
    (let [report-result-filtered (filter
                                   (fn [[url data]]
                                     (and
                                       ;((:check-fn config) url)
                                       (integer? (:status data))
                                       (not= (:status data) 200)))
                                   @*result)
          report-result (map (fn [[url data]] {:url  url
                                               :from (:from data)}) report-result-filtered)]
      ;(println "Print report, urls for check: " (count (get-urls-for-check *result (:check-fn config))) ", total: " (count @*result))
      (when (:end-fn config)
        ((:end-fn config) report-result)))))

(defn run-requests [*result config]
  (swap! (:*loop-count config) inc)
  (let [urls-for-check-total (get-urls-for-check *result (:check-fn config))
        urls-for-check-total-count (count urls-for-check-total)
        urls-for-check (take 100 urls-for-check-total)
        urls-count (count urls-for-check)
        *current-count (atom 0)]
    (if (make-iteration? urls-count config)
      (do
        ((:iteration-fn config) @(:*loop-count config) urls-count urls-for-check-total-count (count @*result))


        (client/with-async-connection-pool {:threads 8 :default-per-route 12}
                                           [(doseq [[url _] urls-for-check]
                                              (check-url-async url *result urls-count *current-count run-requests config))]))


      (iteration-result *result config))))

;;======================================================================================================================
;; Start
;;======================================================================================================================

(defn init-config [config]
  (assoc config
    :*loop-count (atom 0)
    :iteration-fn (or (:iteration-fn config) (constantly true))
    :max-loop-count (or (:max-loop-count config) Integer/MAX_VALUE)
    :default-protocol (or (:default-protocol config) "https")))

(defn init-result [urls init-url]
  (reduce (fn [res url]
            (assoc res url {:from [{:url   init-url
                                    :links [{:href init-url
                                             :text init-url}]}]}))
          {}
          urls))

(defn start-by-urls [urls init-url config]
  (let [config (init-config config)
        *result (atom (init-result urls init-url))]
    (run-requests *result config)))



(defn start-by-sitemap-url [sitemap-url config]
  (let [sitemap-urls (map link-checker.url/prepare-url (urls-from-sitemap sitemap-url))]
    (start-by-urls sitemap-urls sitemap-url config)))



;;======================================================================================================================
;; Testing
;;======================================================================================================================

;(defn domain-url [domain]
;  (case domain
;    :stg "http://docs.anychart.stg/"
;    :prod "https://docs.anychart.com/"
;    :local "http://localhost:8080/"))
;
;
;(defn get-check-fn [version-key domain]
;  (fn [url]
;    (and (not (.contains url "export-server.jar"))
;         (not (.endsWith url (str "/" version-key "/download")))
;         (.contains url (str (domain-url domain) version-key "/")))))
;
;(defn get-add-fn [version-key domain]
;  (fn [url]
;    (if (and (.startsWith url (domain-url domain))
;             (not (.startsWith url (str (domain-url domain) version-key "/"))))
;      false
;      true)))
;
;(defn get-sitemap-urls [version-key domain]
;  (let [sitemap-url (str (domain-url domain) "sitemap/" version-key)
;        sitemap-urls (map link-checker.url/prepare-url (urls-from-sitemap sitemap-url))
;        sitemap-urls (map
;                       (fn [s] (case domain
;                                 :prod s
;                                 :stg (-> s
;                                          (clojure.string/replace #"\.com" ".stg")
;                                          (clojure.string/replace #"https:" "http:"))
;                                 :local (-> s (clojure.string/replace #"https://docs\.anychart\.com" "http://localhost:8080"))))
;                       sitemap-urls)]
;    sitemap-urls))
;
;(defn check-broken-links [version-key domain]
;  (let [sitemap-url (str (domain-url domain) "sitemap/" version-key)
;        sitemap-urls (get-sitemap-urls version-key domain)
;        config {:check-fn         (get-check-fn version-key domain)
;                :add-fn           (get-add-fn version-key domain)
;                :iteration-fn     (fn [iteration urls-count urls-for-check-total-count total-count]
;                                    (println "Iteration: " iteration urls-count urls-for-check-total-count total-count))
;                :max-loop-count   40
;                :default-protocol "http"
;                :end-fn           (fn [res]
;                                    (prn "END: " res)
;                                    (prn "REVERT END: " (link-checker.utils/revert-result res))
;                                    )}]
;    (start-by-urls sitemap-urls sitemap-url config)))

;;======================================================================================================================
;; Result struct
;;======================================================================================================================
(comment
  {
   "https://docs.anychart.com/8.0.1/Quick_Start/Quick_Start"  {:from   [{:url   "https://docs.anychart.com/8.0.1/sitemap"
                                                                         :links [{:href "https://docs.anychart.com/8.0.1/sitemap"
                                                                                  :text "sitemap"}]}]
                                                               :status 200}

   "https://docs.anychart.com/8.0.1/Quick_Start/Credits"      {:from [{:url   "https://docs.anychart.com/8.0.1/sitemap"
                                                                       :links [{:href "https://docs.anychart.com/8.0.1/sitemap"
                                                                                :text "sitemap"}]}]}

   "https://docs.anychart.com/8.0.1/Quick_Start/Credits#asdf" {:from [{:url   "https://docs.anychart.com/8.0.1/sitemap"
                                                                       :links [{:href "https://docs.anychart.com/8.0.1/sitemap"
                                                                                :text "sitemap"}]}]}

   "https://docs.anychart.com/7.14.3/Quick_Start/Credits"     {:from [{:url   "https://docs.anychart.com/8.0.1/Quick_Start/Credits"
                                                                       :links [{:href "https://docs.anychart.com/8.0.1/sitemap"
                                                                                :text "sitemap"}]}]}

   "http://api.anychart.com/"                                 {:from [{:url   "https://docs.anychart.com/8.0.1/Quick_Start/Credits"
                                                                       :links [{:href "http://anychart.com"
                                                                                :text "AnyChart"}]}]}

   "http://anychart.com/"                                     {:from [{:url   "https://docs.anychart.com/8.0.1/Quick_Start/Credits"
                                                                       :links [{:href "http://anychart.com"
                                                                                :text "AnyChart"}]}]}
   })

(comment
  [
   {
    :url  "http://api.anychart.stg/ilevd-test/anychart.enums.GanttDataFields#CONNECT_TO"
    :from ({:url   "http://docs.anychart.stg/ilevd-test/Gantt_Chart/Project_Chart#tasks_types"
            :links ({:href "//api.anychart.stg/ilevd-test/anychart.enums.GanttDataFields#CONNECT_TO"
                     :text "connectTo)"})}
            {:url   "http://docs.anychart.stg/ilevd-test/Gantt_Chart/Project_Chart"
             :links ({:href "//api.anychart.stg/ilevd-test/anychart.enums.GanttDataFields#CONNECT_TO"
                      :text "connectTo)"})})
    }

   {:url    "https://docs.anychart.com/8.0.1/Quick_Start/Quick_Start"
    :from   [{:url   "https://docs.anychart.com/8.0.1/sitemap"
              :links [{:href "https://docs.anychart.com/8.0.1/sitemap"
                       :text "sitemap"}]}]
    :status 200}

   {:url  "https://docs.anychart.com/8.0.1/Quick_Start/Credits"
    :from [{:url   "https://docs.anychart.com/8.0.1/sitemap"
            :links [{:href "https://docs.anychart.com/8.0.1/sitemap"
                     :text "sitemap"}]}]}

   "https://docs.anychart.com/8.0.1/Quick_Start/Credits#asdf" {:from [{:url   "https://docs.anychart.com/8.0.1/sitemap"
                                                                       :links [{:href "https://docs.anychart.com/8.0.1/sitemap"
                                                                                :text "sitemap"}]}]}

   "https://docs.anychart.com/7.14.3/Quick_Start/Credits" {:from [{:url   "https://docs.anychart.com/8.0.1/Quick_Start/Credits"
                                                                   :links [{:href "https://docs.anychart.com/8.0.1/sitemap"
                                                                            :text "sitemap"}]}]}

   "http://api.anychart.com/" {:from [{:url   "https://docs.anychart.com/8.0.1/Quick_Start/Credits"
                                       :links [{:href "http://anychart.com"
                                                :text "AnyChart"}]}]}

   "http://anychart.com/" {:from [{:url   "https://docs.anychart.com/8.0.1/Quick_Start/Credits"
                                   :links [{:href "http://anychart.com"
                                            :text "AnyChart"}]}]}
   ])
