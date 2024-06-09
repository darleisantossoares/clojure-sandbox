(ns sandbox.d-schema
  (:require [datomic.api :as d]
            [clojure.data :refer [diff]]
            [clojure.walk :refer [postwalk]]
            [clojure.pprint :as pp :refer [pprint]]
            [clojure.string :as str])
  (:import [java.util Calendar Date]))

(def uri "datomic:dev://localhost:4334/tts")

(defn get-schema [db]
  (let [schema-entities (d/q '[:find ?e
                               :where
                               [?e :db/ident ?ident]
                               [?e :db/valueType]
                               (not [(clojure.string/starts-with? (str ?ident) ":db")])
                               (not [(clojure.string/starts-with? (str ?ident) ":fressian")])]
                             db)]
    (map (fn [eid]
           (d/pull db '[*] eid))
         (map first schema-entities))))

(defn format-schema [db schema]
  (map (fn [entity]
         (let [valueType-id (:db/valueType entity)
               cardinality-id (:db/cardinality entity)
               unique-id (:db/unique entity)
               tupleAttrs-ids (:db/tupleAttrs entity)
               indexed? (:db/index entity)]
           {:db/ident (:db/ident entity)
            :db/valueType (:db/ident (d/entity db (:db/id valueType-id)))
            :db/cardinality (:db/ident (d/entity db (:db/id cardinality-id)))
            :db/index (if (some? indexed?) indexed? false)
            :db/unique (when unique-id
                         (:db/ident (d/entity db (:db/id unique-id))))
            :db/tupleAttrs (when tupleAttrs-ids
                             tupleAttrs-ids)})) schema))

(defn unnamespace-key [k]
  (if (keyword? k)
    (keyword (name k))
    k))


(defn unnamespace [model]
  (postwalk
   (fn [x]
     (if (map? x)
       (into {} (remove (fn [[k v]] (nil? v))
                        (map (fn [[k v]] [(unnamespace-key k) v]) x)))
       x)) model))

(defn retrieve-schema [& [as-of]]
  (let [conn (d/connect uri)
        db (if as-of (d/as-of (d/db conn) as-of) (d/db conn))
        schema (get-schema db)
        formatted-schema (format-schema db schema)]
    (vec (map unnamespace formatted-schema))))

(defn yesterday []
  (let [cal (Calendar/getInstance)]
    (.add cal Calendar/DAY_OF_MONTH -1)
    (.getTime cal)))

;; Example usage
(println "Schema as of now:")
(pprint (retrieve-schema))

(println "\nSchema as of yesterday:")
(let [as-of (yesterday)] ; get the timestamp for yesterday
  (pprint (retrieve-schema as-of)))


(def data-schema-now (retrieve-schema))

(println data-schema-now)

(def data-schema-yesterday (retrieve-schema (yesterday)))

(println data-schema-yesterday)

#_(println (diff data-schema-now data-schema-yesterday))

