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