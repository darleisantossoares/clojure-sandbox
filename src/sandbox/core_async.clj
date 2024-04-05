(ns sandbox.core-async
  (:require [clojure.core.async :refer [chan <!! >!!]]))


(let [c (chan)]
  (future (dotimes [x 10]
            (>!! c x)))
  (future (dotimes [x 10]
            (>!! c (* x 3))))
  (future (dotimes [_ 20]
            (println (<!! c)))))



