(ns sandbox.concurrency-book)


(def donation-count (atom 0))


(defn tweet [msg]
  (println msg))

(defn book []
  
  (dotimes [_ 9]
    (doto (Thread. (fn []
                     (Thread/sleep 300)
                     (swap! donation-count + 10)
                     (recur)))
      .start))

  (doto (Thread. (fn []
                   (Thread/sleep 10)
                   (tweet (str "We collected $" @donation-count " Total"))))
    .start))

(book)
