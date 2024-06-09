(ns sandbox.dedup-tips-and-tricks
  (:require [datomic.api :as d]
            [clojure.pprint :as pp :refer [pprint]]
            [clojure.string :as str]))

(def db-uri "datomic:dev://localhost:4334/tts")

(defn create-database
  "Creates a database"
  [uri]
  (d/create-database uri))

;; call to create the database
(create-database db-uri)

;; creates a connection
(def conn (d/connect db-uri))

(def orders-schema-customer
  [{:db/ident :customer/id
    :db/valueType :db.type/uuid
    :db/unique :db.unique/identity
    :db/cardinality :db.cardinality/one}
   {:db/ident :customer/email
    :db/valueType :db.type/string
    :db/unique :db.unique/identity
    :db/cardinality :db.cardinality/one}])

(def orders-schema-order
  [{:db/ident :order/id
    :db/valueType :db.type/uuid
    :db/unique :db.unique/identity
    :db/cardinality :db.cardinality/one}
   {:db/ident :order/customer-id
    :db/valueType :db.type/uuid
    :db/index true
    :db/cardinality :db.cardinality/one}
   {:db/ident :order/customer
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one}
   {:db/ident :order/placed-at
    :db/valueType :db.type/instant
    :db/cardinality :db.cardinality/one}
   {:db/ident :order/status
    :db/valueType :db.type/keyword
    :db/cardinality :db.cardinality/one}
   {:db/ident :order/product-id
    :db/valueType :db.type/uuid
    :db/cardinality :db.cardinality/one
    :db/index true}
   {:db/ident :order/product
    :db/cardinality :db.cardinality/one
    :db/valueType :db.type/ref}])

(def orders-schema-product
  [{:db/ident :product/id
    :db/valueType :db.type/uuid
    :db/unique :db.unique/identity
    :db/cardinality :db.cardinality/one}
   {:db/ident :product/name
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident :product/price
    :db/valueType :db.type/float
    :db/cardinality :db.cardinality/one}])

(defn transact-schema
  "receives a schema and transact it"
  [connection schema]
  @(d/transact connection schema))

;; transacting orders, customers, and product schema
(transact-schema conn orders-schema-customer)
(transact-schema conn orders-schema-order)
(transact-schema conn orders-schema-product)


(defn generate-random-email
  "generates random emails"
  []
  (let [chars "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        suffix "@tips-and-tricks.com"
        email-length (+ 5 (rand-int 14))
        prefix (str/join (repeatedly email-length #(nth chars (rand-int (count chars)))))]
    (str prefix suffix)))


(defn generate-order 
  "Generates the data map for an order."
  [db customer-eid product-eid]
  {:db/id (d/tempid :db.part/user)
   :order/id (d/squuid)
   :order/customer-id (:customer/id (d/entity db customer-eid))
   :order/customer customer-eid
   :order/placed-at (java.util.Date.)
   :order/status :order.status/pending
   :order/product-id (:product/id (d/entity db product-eid))
   :order/product product-eid})

; insert 100 customers
(doseq [_ (range 100)]
  @(d/transact conn [{:customer/id (d/squuid)
                      :customer/email (generate-random-email)}]))

;insert 10 different products
(doseq [x (range 10)]
  @(d/transact conn [{:product/id (d/squuid)
                      :product/name (str "product " x)
                      :product/price (+ 0.1 (* (rand) 99.9))}]))



(defn fetch-random-customer [db]
  (let [result (d/q '[:find (rand ?e) .
                      :where [?e :customer/id]] db)]
    result))

(defn fetch-random-product [db]
  (let [result (d/q '[:find (rand ?e) . :where [?e :product/id]] db)]
    result))


(defn create-n-orders
  "Creates n orders to with random data"
  [conn n]
  (let [db (d/db conn)]
    (doseq [_ (range n)]
      (let [customer-eid (fetch-random-customer db)
            product-eid (fetch-random-product db)
            order (generate-order db  customer-eid product-eid)]
        @(d/transact conn [order])))))

; creates 1000 orders
(create-n-orders conn 1000)

; count of the total orders in our database
(println (d/q '[:find (count ?e) :where [?e :order/id]] (d/db conn)))

(rand-nth (map first (d/q '[:find ?order-id :where [?e :order/id ?order-id]] (d/db conn))))


(let [order-id-random (rand-nth (map first (d/q '[:find ?order-id :where [?e :order/id ?order-id]] (d/db conn))))
      order (d/q '[:find (pull ?e [*]) . :where [?e :order/id  ?order-id-random]] (d/db conn) order-id-random)
      new-order (assoc order :order/status :order.status/delivered)
      tx-result (d/transact conn [new-order] :io-context :tips-and-tricks/dedup)
      ]
  (println new-order)
  (println "=============")
  (println @tx-result))


(let [order-id-random (rand-nth (map first (d/q '[:find ?order-id :where [?e :order/id ?order-id]] (d/db conn))))
      new-order [:db/add [:order/id order-id-random] :order/status :order.status/delivered]
      tx-result (d/transact conn [new-order] :io-context :tips-and-tricks/dedup)])


(def darlei-order (rand-nth (map first (d/q '[:find ?order-id :where [?e :order/id ?order-id]] (d/db conn)))))

(println darlei-order)




