(ns sandbox.datomic-channel
  (:require [datomic.api :as d]
            [clj-http.client :as client]))


#_(def schema
  [{:db/ident :customer-portifolio/customer-id
    :db/valueType :db.type/uuid
    :db/cardinality :db.cardinality/one}

   {:db/ident :customer-portifolio/stock-code
    :db/valueType :db.type/keyword
    :db/cardinality :db.cardinality/one
    :db/unique :db.unique/value}

   {:db/ident :customer-portifolio/total
    :db/valueType :db.type/bigint
    :db/cardinality :db.cardinality/one}
   
   {:db/ident :customer-portifolio/stock
    :db/cardinality :db.cardinality/one
    :db/valueType :db.type/ref}

   {:db/ident :customer-portifolio/customer-id+stock-code
    :db/cardinality :db.cardinality/one
    :db/valueType :db.type/tuple
    :db/tupleAttrs [:customer-portifolio/customer-id :customer-portifolio/stock-code]
    :db/unique :db.unique/identity}])



(def schema-jessica
  [{:db/ident :bbb/jessica-id
    :db/valueType :db.type/uuid
    :db/cardinality :db.cardinality/one
    :db/index true}
   {:db/ident :bbb/stock-code
    :db/valueType :db.type/keyword
    :db/cardinality :db.cardinality/one}])


(def schema-change
  [{:db/ident :bbb/amiga-id
    :db/valueType :db.type/uuid
    :db/cardinality :db.cardinality/one}])

(defn create-database [uri]
  (d/create-database uri))


(def db-uri "datomic:dev://localhost:4334/jessica")

(create-database db-uri)

(def conn (d/connect db-uri))


@(d/transact conn schema-jessica)

@(d/transact conn schema-change)


(defn generate-random-3-chars-keyword
  []
  (let [chars (range 65 91)
        pick-random-char (fn [] (char (nth chars (rand-int (count chars)))))]
    (keyword (str (pick-random-char) (pick-random-char) (pick-random-char)))))


(println (generate-random-3-chars-keyword))


(doseq [_ (range 5)]
  @(d/transact conn  [{:bbb/jessica-id (d/squuid)
                       :bbb/stock-code (generate-random-3-chars-keyword)
                       :bbb/amiga-id (d/squuid)}]))


(println (d/query {:query '[:find (pull ?e[*])
                    :in $
                    :where [?e :bbb/jessica-id ?]]
           :args [(d/db conn)]}))



(println (d/query {:query '[:find (count ?e)
                    :in $
                    :where [?e :bbb/jessica-id ?]]
           :args [(d/db conn)]}))


(println "##################################################################")


(println (d/query {:query '[:find (count ?e)
                    :in $
                    :where [?e :jessica/jessica-id ?]
                            [(missing? $ ?e :jessica/amiga-id)]]
           :args [(d/db conn)]}))



(println (d/query {:query '[:find (pull ?e[*])
                    :in $
                    :where [?e :bbb/jessica-id ?]
                            [(missing? $ ?e :bbb/amiga-id)]]
           :args [(d/db conn)]}))

(defn leitura-do-banco
  []
  (d/index-pull (d/db conn) {:index :avet
                             :start [:bbb/jessica-id]
                             :selector [:bbb/jessica-id :bbb/stock-code :bbb/amiga-id]
                             }))

(def banco-de-dados-inteiro (leitura-do-banco))



(require '[clojure.pprint :as pp])


(pp/pprint banco-de-dados-inteiro)

(defn add-no-quarto-gnomo
  [jessica]
  (let [nova-jessica (assoc jessica :bbb/amiga-id (d/squuid))]
      @(d/transact conn [nova-jessica])))


(doseq [u banco-de-dados-inteiro]
  (when-not (:bbb/amiga-id u)
        (add-no-quarto-gnomo u)))


(defn permutations
  [s]
  (println s)
  (if (empty? s)
    '(())
    (for [x s
          p (permutations (remove #{x} s))]
      (cons x p))))

(println (permutations [1 2 3]))



