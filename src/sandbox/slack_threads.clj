(ns sandbox.slack-threads
  (:require [datomic.api :as d]
            [clojure.pprint :as pp :refer [pprint]]))


(def db-uri "datomic:dev://localhost:4334/ti-ti-ti")


(def schema
  [{:db/ident :decision/id
    :db/valueType :db.type/uuid
    :db/index true
    :db/unique :db.unique/identity
    :db/cardinality :db.cardinality/one}
   {:db/ident :decision/final
    :db/valueType :db.type/boolean
    :db/cardinality :db.cardinality/one}
   {:db/ident :decision/random-string
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident :audit/user
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident :audit/tx
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/unique :db/unique}])


(defn create-database
  [uri]
  (d/create-database uri))

(create-database db-uri)

(def conn (d/connect db-uri))


@(d/transact conn schema)

(defn random-bool []
  (< (rand) 0.5))


(defn generate-random-string
  []
  (let [chars (range 65 91)
        pick-random-char (fn [] (char (nth chars (rand-int (count chars)))))]
    (str (pick-random-char) (pick-random-char) (pick-random-char))))


(doseq [_ (range 50)]
  (let [d-id (d/squuid)
        _ @(d/transact conn [{:decision/id d-id
                              :decision/final (random-bool)
                              :decision/random-string (generate-random-string)}])]
    @(d/transact conn [{:audit/user "me"
                        :audit/tx {:decision/id d-id} }])))




(pprint (d/query {:query '[:find (pull ?e[*])
                    :in $
                    :where [?e :decision/id ?]]
           :args [(d/db conn)]}))

(def story [:decision/id  #uuid "6605cf10-e7e6-4ce4-b830-c0a112e3cde2"])

(->> (d/q '[:find ?aname ?v ?tx ?inst ?added
            :in $ ?e
            :where
            [?e ?a ?v ?tx ?added]
            [?a :db/ident ?aname]
            [?tx :db/txInstant ?inst]]
          (d/history (d/db conn))
          story)
     seq
     (sort-by #(nth % 2))
     pprint)


@(d/transact conn [{:decision/id  #uuid "6605cf10-e7e6-4ce4-b830-c0a112e3cde2"
                    :decision/final false
                    :decision/random-string "I changed for Slack"}])



(defn permutations
  [clauses]
  (if (empty? clauses)
    '(())
    (for [x clauses
          p (permutations (remove #{x} clauses))]
      (vec (cons x p)))))


(def query
  '{:find  [?con]
   :where [[?con :decision/id #uuid  "660afc1f-d616-4cc6-80f1-3ffb713b92ce"]
           [?con :decision/final false]]
   :in    [$]})


(defn permutate-query
  [user-query]
  (for [clause (permutations (:where user-query))]
    (let [new-query (assoc user-query :where clause)
          res (d/query {:query new-query
                        :args [(d/db conn)]
                        :query-stats true})
          clauses (:clauses (first (get-in res [:query-stats :phases])))]
      {
         :result res
         :total-line-reads (reduce + (map :rows-out clauses))})))


(pprint (permutate-query query))






