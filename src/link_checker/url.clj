(ns link-checker.url
  (:require [cemerick.url :as url-utils]
            [clojure.string :as string])
  (:import (java.net URL)))


(defn base-path [url]
  (let [u (URL. url)
        path (.getPath u)
        base (subs url
                   0
                   (- (count url) (count path)))]
    base))


(defn prepare-url [url]
  (let [path (.getPath (URL. url))
        base (base-path url)
        encoded-path (string/join "/"
                                  (map url-utils/url-encode
                                       (string/split path #"/")))]
    (str base encoded-path)))