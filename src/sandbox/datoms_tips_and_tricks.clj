(ns sandbox.datoms-tips-and-tricks
  (:require [datomic.api :as d]
            [clojure.pprint :as pp :refer [pprint]]
            [clojure.string :as str]))

(:require '[[datomic.api :as d]
            [clojure.pprint :as pp :refer [pprint]]
            [clojure.string :as str]])


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

(defn generate-random-email
  "generates random emails"
  []
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

(d/q '[:find (count ?x) :where [?x :order/id]] db)


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

;(def db (d/db conn))

(pprint (:customer/id (d/entity db (fetch-random-customer db))))

(pprint (generate-order db (fetch-random-customer db) (fetch-random-product db)))

@(d/transact conn [(generate-order db (fetch-random-customer db) (fetch-random-product db))])

(create-n-orders conn 500000)

(d/q '[:find (count ?e) :where [?e :order/id]] db)

(d/q '[:find ?customer-id :where [?e :order/customer-id ?customer-id]] db)

(def random-customer-id (rand-nth (map first (d/q '[:find ?customer-id :where [?e :order/customer-id ?customer-id]] db))))

(println random-customer-id)

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



;;;;;;;;;;;;;



(def customer (d/q '[:find (pull ?e [:order/customer]) :where [?e :order/id #uuid "66554eac-a8d2-4c0d-90ee-1542c27199ff"]] db))

(def order-id  #uuid "66554eac-a8d2-4c0d-90ee-1542c27199ff")


(println (:order/customer customer))

(datoms-query db {:api :datoms
                  :opts {:index :avet
                         :attr :order/customer
                         :values [customer]}})




(let [db (d/db conn)
      eid (ffirst (d/index-range db :order/id order-id order-id))]
  (pprint eid)
  (pprint (d/pull db '[*] eid)))

(println customer)


(d/entid db customer)




(pprint (d/q '[:find (pull ?e [{:order/customer [*]}]) :where [?e :order/id #uuid "66554eac-a8d2-4c0d-90ee-1542c27199ff"]] db))



#_(def customer-id (d/q '[:find ?customer-id :where [?e :customer/id #uuid "6655441d-3530-46f5-a15d-e7834405d146"]
                        [?customer-id :customer/id ?e]] db))

(def customer-id #uuid "6655441d-3530-46f5-a15d-e7834405d146")



(d/index-pull db {:index :avet :selector '[*] :start customer-id})




(defn index-pull-order-by-id [order-id]
  (let [db (d/db conn)
        eids (map first (d/index-range db :order/id order-id order-id))]
    (print eids)
    (mapv #(d/pull db '[*] %) eids)))


(d/index-range db :order/id order-id order-id)

(d/q '[:find ?e :where [?e :order/customer-id customer-id]] db)


(map (fn [eid] (d/pull db '[*] eid)) (d/index-range db :order/customer-id customer-id customer-id))

(pprint (index-pull-order-by-id order-id))

(pprint order-id)

(index-pull-order-by-id order-id)

(d/entity-db (d/entity db 17592186045618))



(datoms-query db {:api :datoms
                  :opts {:index :avet
                         :attr :order/customer-id
                         :values [customer-id]}})


(d/part 17592186045618)


(defn get-customer-orders [db customer-id]
  (d/index-pull db
                {:index :avet
                 :start [:order/customer-id customer-id]
                 :reverse false
                 :limit nil
                 :selector [:order/id :order/placed-at :order/status :order/product :order/customer-id]}))


(pprint random-customer-id)

(get-customer-orders db random-customer-id)


(doseq [order (get-customer-orders db random-customer-id)]
  (println order))


(def customer-orders (vec (take-while
       (fn [order] (= (:order/customer-id order) random-customer-id))
       (get-customer-orders db random-customer-id))))

(count customer-orders)

(doseq [order customer-orders]
  (println (:order/id order)))

(print customer-orders)




;;;;; tips and tricks on how to update data.




(print "random customer ->" random-customer-id)


(def random-order-id #uuid "665548d8-ea94-4686-a66f-316de390c916")

(print random-order-id)


(defn get-order-data
  [par-order-id db]
  (d/q '[:find (pull ?e [*])
         :where [?e :order/id  #uuid "665548d8-ea94-4686-a66f-316de390c916"]] db))

(let [order (d/q '[:find (pull ?e [*]) . :where [?e :order/id  #uuid "665548d8-ea94-4686-a66f-316de390c916"]] db)
      new-order (assoc order :order/status :order.status/delivered)
      tx-result @(d/transact conn [new-order] :io-context :tips-and-tricks/dedup)]
  (println tx-result))


(def random-order (d/q '[:find  ?e . :where [?e :order/id #uuid "665548d8-ea94-4686-a66f-316de390c916"]] db))

(println random-order)


(def order-entity (d/entity db random-order))


(:order/id order-entity)





(let
 [order-id (:order/id order-entity)]
  @(d/transact
    conn
    {:tx-data [{:order/id order-id :order/status :order.status/darlei}]}))


(println (d/q '[:find  (pull ?e[*])  :where [?e :order/id #uuid "665548d8-ea94-4686-a66f-316de390c916"]] (d/db conn)))

(println (:order/id order-entity))


(let [order-id #uuid "665548d8-ea94-4686-a66f-316de390c916"
      tx-result (d/transact conn [{:order/id order-id :order/status :order.status/d}] :io-context :tx/example)]
  (println tx-result))










