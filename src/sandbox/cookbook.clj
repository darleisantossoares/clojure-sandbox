(ns sandbox.cookbook
  (:require [clojure.string :as str]))

(str/blank? "")


(defn bar [_]
  (throw (ex-info "blargh" {})))

(defn safe-foo [x]
  (try
    (map bar x)
    (catch Exception _ nil)))


(empty? (safe-foo nil))
(true? (safe-foo nil))


(safe-foo [])

(safe-foo [:a :b :c])

