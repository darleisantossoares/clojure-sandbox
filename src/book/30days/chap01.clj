(ns book.30days.chap01)


(def counter (atom 0))

(swap! counter inc)

(def greetings-atom (atom "Hello"))

(swap! greetings-atom #(str % " World!"))

@greetings-atom

(def my-list (list 1 2 3 4 5))

(first my-list)


(def favorite-fruits ["mango" "apple" "banana"])

(first favorite-fruits)

(last favorite-fruits)

(rest favorite-fruits)


(def person {:name "Alice" :age 30})

(:name person)

(get person :age)


;;;;;;;;;;;;


(defn add [x y]
  (+ x y))

(add 1 1)
(add 3 5)


((fn [x y] (+ x y)) 3 5)

;; high order functions are functions that take other fns as arguments
;; or return fns as results

(defn apply-twice [f x]
  (f (f x)))

(apply-twice inc 5)


;;;;;;;;;;;;


(defn calculate-area
  [length width]
  (* length width))

(calculate-area 5 7)

(defn greet
  [name]
  (str "Hello, " name))

(def user-name "foo")

(greet user-name)

;;;;;;;;;;;;


(def names ["Alice" "Bob" "Charlie"])

(def updated-names (conj names "David"))

updated-names


