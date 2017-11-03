(ns link-checker.url
  (:require [cemerick.url :as url-utils]
            [clojure.string :as string])
  (:import (java.net URL)))


(defn base-path [u]
  (let [u (URL. u)]
    (str (.getProtocol u)
         "://"
         (.getHost u)
         (when (pos? (.getPort u))
           (str ":" (.getPort u))))))


(defn prepare-url [u]
  (let [url (URL. u)
        path (.getPath url)
        query (.getQuery url)
        ref (.getRef url)
        base (base-path u)
        encoded-path (string/join "/"
                                  (map #(url-utils/url-encode
                                          (try (url-utils/url-decode %)
                                               (catch Exception e
                                                 %)))
                                       (string/split path #"/")))]
    (str base
         encoded-path
         (when query (str "?" query))
         (when ref (str "#" ref)))))