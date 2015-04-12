(ns multco.storage
  (:require [clodexeddb.core :as cldb]
            [cljs.core.async :refer [put! <! chan]]
            [cljs.reader :as reader]
            [fogus.datalog.bacwn.impl.rules :as brules]
            [fogus.datalog.bacwn.impl.literals :as blit]
            [fogus.datalog.bacwn.impl.database :as bdb])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(defn setup-db
  "The database for an app to use; can hold multple
  Multco databases inside it."
  [name]
  (cldb/setup name))

(comment (def test-db (setup-db "test")))

(defn rm-db
  "Deletes all the Multco databases for a program
  (removes the whole IndexedDB database)."
  [db]
  (cldb/rm-db db))

(comment (cldb/rm-db "test"))

;; NOTE: keeping db as a string because cljs cannot hold the JS
;; return value of setup-db
;; NOTE: the multco databases are stored as objects in the IDB object stores
;; therefore they're labeled "obj"
(defn add-obj
  "Adds a Multco database (add an object to the
  object store)."
  [db obj val]
  (cldb/add (setup-db db) "database" {:name obj
                                      :value (pr-str val)}))

(comment (add-obj "test" "db-name" "some cljs database stuff"))

(defn clear-obj
  "Removes one Multco database (removes an object
  from the object store)."
  [db obj]
  (cldb/clear (setup-db db) "database" obj))

(comment (clear-obj "test" "db-name"))

(defn get-obj
  "This tries to get a multco db from the database and returns the result
  asynchronously on a channel. When the value exists, the channel
  is returned with the value. Otherwise the channel returns a value
  of :empty."
  [db obj]
  (let [cb (chan)]
    (cldb/get-query (setup-db db) "database" obj
      (fn [e] (let [v (get e "value")]
               (if (seq v)
                 (put! cb v)
                 (put! cb :empty)))))
    cb))

;; bacwn tagged literals
(reader/register-tag-parser! "fogus.datalog.bacwn.impl.literals.AtomicLiteral"
  blit/map->AtomicLiteral)
(reader/register-tag-parser! "fogus.datalog.bacwn.impl.rules.DatalogRule"
  brules/map->DatalogRule)
(reader/register-tag-parser! "fogus.datalog.bacwn.impl.database.Relation"
  bdb/map->Relation)

;; FIXME: when :empty is returned, core.async throws
;;  "Uncaught Error: Request:a is not ISeqable"
(defn atom-lookup
  "Takes IDB database info (db + store), a Multco atom, and facts for a cljs
  database. It tries to instantiate a new Multco atom. If the db exists,
  the Multco atom gets set to the retrieved value, else the atom is set
  to the new facts."
  [db obj atm facts]
  (go
    (let [res (<! (get-obj db obj))]
      (if (= :empty res)
        (doall (reset! atm facts)
          (add-obj db obj facts)) ;add the facts to db when empty
        (reset! atm (reader/read-string res))))))
