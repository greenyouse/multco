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
  [store]
  (cldb/rm-db store))

(comment (cldb/rm-db "test"))

;; NOTE: keeping db as a string because cljs cannot hold the JS
;; return value of setup-db
(defn add-db
  "Adds a Multco database (add an object to the
  object store)."
  [db store val]
  (cldb/add (setup-db db) "database" {:name store
                                      :value (pr-str val)}))

(comment (add-db "test" "db-name" "some cljs database stuff"))

(defn clear-db
  "Removes one Multco database (removes an object
  from the object store)."
  [db store]
  (cldb/clear (setup-db db) "database" store))

(comment (clear-db "test" "db-name"))

;; NOTE: The "stores" that we're using are just IDB objects. Actual IDB
;; ObjectStores are not used.
(defn get-db
  "This tries to get a multco db from the database and returns the result
  asynchronously on a channel. When the value exists, the channel
  is returned with the value. Otherwise the channel returns a value
  of :empty."
  [db store]
  (let [cb (chan)]
    (cldb/get-query (setup-db db) "database" store
      (fn [e] (let [v (get e "value")]
               (if (seq v)
                 (put! cb v)
                 (put! cb :empty)))))
    cb))

;; bacwn tagged literals
(cljs.reader/register-tag-parser! "fogus.datalog.bacwn.impl.literals.AtomicLiteral"
  blit/map->AtomicLiteral)
(cljs.reader/register-tag-parser! "fogus.datalog.bacwn.impl.rules.DatalogRule"
                                  brules/map->DatalogRule)
(cljs.reader/register-tag-parser! "fogus.datalog.bacwn.impl.database.Relation"
                                  bdb/map->Relation)

(defn atom-lookup
  "Takes IDB database info (db + store), a Multco atom, and facts for a cljs
  database. It tries to instantiate a new Multco atom. If the db exists,
  the Multco atom gets set to the retrieved value, else the atom is set
  to the new facts."
  [db store atm facts]
  (go
    (let [res (<! (get-db db store))]
      (if (= :empty res)
        (doall (reset! atm facts)
          (add-db db store facts)) ;add the facts to db when empty
        (reset! atm (reader/read-string res))))))

(comment (def woot (atom nil))
         (reset! woot "hi")
         (println @woot)

         (atom-lookup "test" "meow" woot "{:hiya \"moot\"}")

         (clear-db "test" "meow"))
