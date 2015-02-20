(ns pldb-cache.core
  (:require [clodexeddb.core :as cldb]
            [cljs.reader :as reader]
            [cljs.core.logic.pldb :as pldb]
            [cljs.core.async :as async :refer [put! <! chan]]
            ydn.db)
  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

(def ^:private trans-chan (chan))

(defn transactor
  "Transactor to cache the in-memory database asynchronously"
  []
  (go-loop []
    (let [[db name new-db] (<! trans-chan)
          val {:name name :value (pr-str new-db)}]
      (try (cldb/clear db "database" name)
           (cldb/add  db "database" val)
           (catch js/Error e
             (.log js/console "pldb-cache Error: improper message to transactor")))
      (recur))))

;; taken from clodexeddb to save from having to include it in deps
(defn setup
  "Defines a new clientside database."
  [name]
  (transactor)
  (cldb/setup name))

(defn add-facts!
  "Adds new facts and caches the new state.

  db            -- the IndexedDB database defined with clodexeddb.core.setup
  name          -- the text name of ory database, used as a primary
                    key for IndexedDB
  logic-db-atom -- the pldb database being used, stored in an atom
  facts         -- pldb facts to add"
  [db name logic-db-atom & facts]
  (let [new-db (apply pldb/db-facts @logic-db-atom facts)]
    (put! trans-chan [db name new-db])
    (reset! logic-db-atom new-db)))

(defn remove-facts!
  "Removes facts and stores the new state.

  db            -- the IndexedDB database defined with clodexeddb.core.setup
  name          -- the text name of your in-memory database, used as a primary
                    key for IndexedDB
  logic-db-atom -- the pldb database being used, stored in an atom
  facts         -- pldb facts to remove"
  [db name logic-db-atom & facts]
  (let [new-db (apply pldb/db-retractions @logic-db-atom facts)]
    (put! trans-chan [db name new-db])
    (reset! logic-db-atom new-db)))

(defn reset-facts!
  "Resets the logic db to a new set of facts and stores the new state

  db            -- the IndexedDB database defined with clodexeddb.core.setup
  name          -- the text name of your in-memory database, used as a primary
                    key for IndexedDB
  logic-db-atom -- the pldb database being used, stored in an atom
  facts         -- a new set of pldb facts"
  [db name logic-db-atom & facts]
  (let [new-db (apply pldb/db facts)]
    (put! trans-chan [db name new-db])
    (reset! logic-db-atom new-db)))

(defn init-facts
  "Either instantiates new datums if the given database
  does not exist or, if there there's no database, loads an
  existing database from clientside storage.

  db            -- the IndexedDB database defined with clodexeddb.core.setup
  name          -- the text name of your in-memory database, used as a primary
                    key for IndexedDB
  logic-db-atom -- the pldb database being used, stored in an atom
  facts         -- facts from the pldb database"
  [db name logic-db-atom & facts]
  (cldb/get-query db "database" name
                  (fn [e]
                    (if (= e js/undefined)
                      ;; FIXME: apply pldb/db here
                      (let [new-db (apply pldb/db facts)]
                        (put! trans-chan [db name new-db])
                        (reset! logic-db-atom new-db))
                      (let [new-db (-> (get e "value")
                                     (reader/read-string))]
                        (reset! logic-db-atom new-db))))))

(defn clear
  "Erases all state from a database (clears an object store in clientside
  storage)."
  [db name]
  (cldb/clear db "database" name))

(defn rm-db
  "Removes all databases from clientside storage (deletes the IndexedDB database
  for your project). Use the same db name from setup."
  [db-name]
  (cldb/rm-db db-name))
