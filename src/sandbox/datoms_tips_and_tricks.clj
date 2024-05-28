(ns sandbox.datoms-tips-and-tricks
  (:require [datomic.api :as d]
            [clojure.pprint :as pp :refer [pprint]]
            [clojure.string :as str]))

(def db-uri "datomic:dev://localhost:4334/tips-and-tricks")

(defn create-database
  "Creates a database"
  [uri]
  (d/create-database uri))

(create-database db-uri)

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
  [connection schema]
  @(d/transact connection schema))

(transact-schema conn orders-schema-customer)
(transact-schema conn orders-schema-order)
(transact-schema conn orders-schema-product)

(defn generate-random-email []
  (let [chars "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        suffix "@tips-and-tricks.com"
        email-length (+ 5 (rand-int 14))
        prefix (str/join (repeatedly email-length #(nth chars (rand-int (count chars)))))]
    (str prefix suffix)))

; insert 100 customers
(doseq [_ (range 100)]
  @(d/transact conn [{:customer/id (d/squuid)
                      :customer/email (generate-random-email)}]))


(def db (d/db conn))

(d/q '[:find (count ?e) :where [?e :customer/id]] db)


(d/q '[:find (count ?e) :where [?e :product/id]] db)

;insert 10 different products
(doseq [x (range 1000)]
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

(defn generate-order [db customer-eid product-eid]
  ;; Generates the data map for an order.
  {:db/id (d/tempid :db.part/user)
   :order/id (d/squuid)
   :order/customer-id (:customer/id (d/entity db customer-eid))
   :order/customer customer-eid
   :order/placed-at (java.util.Date.)
   :order/status :order.status/pending
   :order/product-id (:product/id (d/entity db product-eid))
   :order/product product-eid})

(defn create-n-orders
  [conn n]
  (let [db (d/db conn)]
    (doseq [_ (range n)]
      (let [customer-eid (fetch-random-customer db)
            product-eid (fetch-random-product db)
            order (generate-order db  customer-eid product-eid)]
        @(d/transact conn [order])))))

(def db (d/db conn))

(pprint (:customer/id (d/entity db (fetch-random-customer db))))

(pprint (generate-order db (fetch-random-customer db) (fetch-random-product db)))

@(d/transact conn [(generate-order db (fetch-random-customer db) (fetch-random-product db))])

(create-n-orders conn 500000)

(d/q '[:find (count ?e) :where [?e :order/id]] db)

(d/q '[:find ?customer-id :where [?e :order/customer-id ?customer-id]] db)

(def random-customer-id (rand-nth (map first (d/q '[:find ?customer-id :where [?e :order/customer-id ?customer-id]] db))))

(d/datoms db :avet (d/entid db :order/customer-id)  random-customer-id)

(pprint "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%")
(doseq [datom (d/datoms db :avet (d/entid db :order/customer-id) random-customer-id)]
  (pprint datom)
  (pprint (:e datom))
  (pprint (:v datom))
  (pprint (:a datom))
  (pprint (:tx datom))
  (pprint (:added datom)))

(doseq [datom (d/datoms db :eavt (d/entid db :order/status))]
  (pprint datom)
  (pprint (:e datom))
  (pprint (:v datom))
  (pprint (:a datom))
  (pprint (:tx datom))
  (pprint (:added datom)))

(pprint (d/query {:args [db random-customer-id]
                  :query-stats true
                  :query '{:find [(count ?orders)]
                           :in [$ ?random-customer-id]
                           :where [[?orders :order/customer-id ?random-customer-id]]}}))

(println random-customer-id)
(println "####################################")

(pprint (d/query {:args [db random-customer-id]
                  :query-stats true
                  :io-context :experiment/repl
                  :query '{:find [(count ?orders)]
                           :in [$ ?random-customer-id]
                           :where [[?orders :order/customer-id ?random-customer-id]]}}))

(time (dotimes [_ 10]
        (time (dotimes [_ 1000]
                (d/query {:args [db random-customer-id]
                          :query '{:find [(count ?orders)]
                                   :in [$ ?random-customer-id]
                                   :where [[?orders :order/customer-id ?random-customer-id]]}})))))

"Elapsed time: 240.280708 msecs"
"Elapsed time: 187.729209 msecs"
"Elapsed time: 194.821458 msecs"
"Elapsed time: 176.135333 msecs"
"Elapsed time: 182.895541 msecs"
"Elapsed time: 185.643333 msecs"
"Elapsed time: 161.3325 msecs"
"Elapsed time: 180.147042 msecs"
"Elapsed time: 167.247125 msecs"
"Elapsed time: 156.826875 msecs"
"Elapsed time: 160.75625 msecs"
"Elapsed time: 1756.003917 msecs"

(time
 (dotimes [_ 10]
   (time
    (dotimes [_ 1000]
      (count (seq (d/datoms db :avet :order/customer-id random-customer-id)))))))

:avet
"Elapsed time: 89.381916 msecs"
"Elapsed time: 39.394209 msecs"
"Elapsed time: 39.953417 msecs"
"Elapsed time: 48.33225 msecs"
"Elapsed time: 27.963958 msecs"
"Elapsed time: 26.616083 msecs"
"Elapsed time: 26.813542 msecs"
"Elapsed time: 27.717916 msecs"
"Elapsed time: 27.999291 msecs"
"Elapsed time: 25.582458 msecs"
"Elapsed time: 381.76625 msecs"

:aevt
"Elapsed time: 42.60125 msecs"
"Elapsed time: 30.842083 msecs"
"Elapsed time: 28.024084 msecs"
"Elapsed time: 27.472375 msecs"
"Elapsed time: 27.522875 msecs"
"Elapsed time: 28.749292 msecs"
"Elapsed time: 27.26625 msecs"
"Elapsed time: 27.313791 msecs"
"Elapsed time: 27.058625 msecs"
"Elapsed time: 29.9105 msecs"
"Elapsed time: 299.105708 msecs"

(defn find-pending-orders
  "This fn will go through the whole database in a lazy way"
  [db]
  (let [status (d/entid db :order/status)]
    (->> (d/datoms db :aevt status)
         (filter #(= :order.status/pending (:v %)))
         (map #(:e %)))))

(find-pending-orders db)

{:api :datoms
 :opts {:index :aevt
        :attr  :order/customer-id
        :value random-customer-id}}

(defmacro datoms-query [db {:keys [api opts]}]
  (let [{:keys [index attr values]} opts
        value (first values)]
    (cond (and (= api :datoms) (or (> (count values) 1) (= (count values) 0)))
          (throw (IllegalArgumentException. "When :api is :datoms, :values must contain one element"))
          (and (= api :datoms) (= (count values) 1))
          `(d/datoms ~db ~index (d/entid ~db ~attr) ~value)
          :else
          (throw (IllegalArgumentException. "The API should be :datoms :query :q")))))

(pprint (macroexpand '(datoms-query
                       db
                       {:api :datoms
                        :opts {:index :avet
                               :attr  :order/customer-id
                               :values [random-customer-id]}})))

(print (macroexpand '(datoms-query
                      db
                      {:api :datoms
                       :opts {:index :avet
                              :attr  :order/customer-id
                              :value random-customer-id}})))

(datoms-query db {:api :datoms
                  :opts {:index :avet
                         :attr :order/customer-id
                         :values [random-customer-id]}})











