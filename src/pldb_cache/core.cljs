(ns pldb-cache.core
  (:require [clodexeddb.core :as cldb]
            [cljs.reader :as reader]
            [cljs.core.logic.pldb :as pldb]
            [cljs.core.async :as async :refer [put! <! chan]])
  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

;; taken from clodexeddb to save from having to include it in deps
(defn setup
  "Defines a new database with a given schema. The schema is standard ydn-db
  style but with ClojureScript syntax. See here for more information:
  http://dev.yathit.com/ydn-db/doc/setup/schema.html"
  [name schema]
  (cldb/setup name schema))

(def ^:private trans-chan (chan))

;; Caches the in-memory database asynchronously
(go-loop []
  (let [[db name new-db] (<! trans-chan)
        val {:name name :value (pr-str new-db)}]
    (try (cldb/clear db "database" name)
         (cldb/add  db "database" val)
         (catch js/Error e
           (.log js/console "pldb-cache Error: improper message to transactor")))
    (recur)))

(defn add-facts!
  "Adds new facts and caches the new state.

  db            -- the IndexedDB database defined with clodexeddb.core.setup
  name          -- the text name of your in-memory database, used as a primary key
                   for IndexedDB
  logic-db-atom -- the pldb database being used, stored in an atom
  facts         -- pldb facts to add"
  [db name logic-db-atom & facts]
  (let [new-db (apply pldb/db-facts @logic-db-atom facts)]
    (put! trans-chan [db name new-db])
    (reset! logic-db-atom new-db)))

(defn remove-facts!
  "Removes facts and stores the new state.

  db            -- the IndexedDB database defined with clodexeddb.core.setup
  name          -- the text name of your in-memory database, used as a primary key
                   for IndexedDB
  logic-db-atom -- the pldb database being used, stored in an atom
  facts         -- pldb facts to remove"
  [db name logic-db-atom & facts]
  (let [new-db (apply pldb/db-retractions @logic-db-atom facts)]
    (put! trans-chan [db name new-db])
    (reset! logic-db-atom new-db)))

(defn init-facts
  "Either instantiates new datums if the given database
  does not exist or, if there there's no database, loads an
  existing database from clientside storage.

  db            -- the IndexedDB database defined with clodexeddb.core.setup
  name          -- the text name of your in-memory database, used as a primary key
                   for IndexedDB
  logic-db-atom -- the pldb database being used, stored in an atom
  facts         -- facts from the pldb database"
  [db name logic-db-atom logic-db]
  (cldb/get-query db "database" name
                  (fn [e]
                    (if (= e js/undefined)
                      (do (put! trans-chan [db name logic-db])
                          (reset! logic-db-atom logic-db))
                      (let [new-db (-> (js->clj e :keywordize-keys true)
                                       (:value)
                                       (reader/read-string))]
                        (reset! logic-db-atom new-db))))))
