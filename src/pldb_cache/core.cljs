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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; DB Atom

(defprotocol IDatabase
  (-pldb-load [this facts]
    "Defines a new clientside database.")
  (-pldb-add! [this facts])
  (-pldb-remove! [this facts])
  (-pldb-reset! [this facts])
  (-pldb-clear! [this delete]))

;; state is the pldb database state, db-state is the value of cldb/setup
;; FIXME: make names for state/db-state better to avoid confusion
(deftype DBAtom [^:mutable state meta validator watches
                 ^string db ^string store ^:mutable db-state]

  IDatabase
  (-pldb-load [this facts]
    (transactor)
    (set! db-state (cldb/setup db))
    (cldb/get-query db-state "database" store
                    (fn [e]
                      (if (= e js/undefined)
                        (-pldb-reset! this facts)
                        (let [new-db (-> (get e "value")
                                         (reader/read-string))]
                          (-reset! this new-db))))))
  (-pldb-add! [this facts]
    (let [new-db (apply pldb/db-facts state facts)]
      (put! trans-chan [db-state store new-db])
      (-reset! this new-db)))
  (-pldb-remove! [this facts]
    (let [new-db (apply pldb/db-retractions state facts)]
      (put! trans-chan [db-state store new-db])
      (-reset! this new-db)))
  (-pldb-reset! [this facts]
    (let [new-db (apply pldb/db facts)]
      (put! trans-chan [db-state store new-db])
      (-reset! this new-db)))
  (-pldb-clear! [this delete]
    (cldb/rm-db db)
    (set! state nil) ;clear the atom
    (set! db-state nil)
    (if-not delete
      (-pldb-load this nil))) ;reload the database (just clears stores)

  IReset
  (-reset! [this new-value]
    (when-not (nil? validator)
      (assert (validator new-value) "Validator rejected reference state"))
    (let [old-value state]
      (set! state new-value)
      (when-not (nil? watches)
        (-notify-watches this old-value new-value))
      new-value))

  IAtom

  IEquiv
  (-equiv [o other] (identical? o other))

  IDeref
  (-deref [_] state)

  IMeta
  (-meta [_] meta)

  IPrintWithWriter
  (-pr-writer [_ writer opts]
    (-write writer "#<DB-Atom: ")
    (pr-writer state writer opts)
    (-write writer ">"))

  IWatchable
  (-notify-watches [this oldval newval]
    (doseq [[key f] watches]
      (f key this oldval newval)))
  (-add-watch [this key f]
    (set! (.-watches this) (assoc watches key f))
    this)
  (-remove-watch [this key]
    (set! (.-watches this) (dissoc watches key)))

  IHash
  (-hash [this] (goog/getUid this)))

(defn db-atom
  "This is a variation of the standard Clojure atom that holds a pldb database
  in memory and caches any pldb updates to clientside storage. The first
  argument, db, is the name of a clientside database for your project. The second
  argument, store, is the name of a pldb store. Generally, a project should only
  use one db to hold all of its pldb stores.

  A new :facts keyword is used to define a template pldb database wrapped in a
  sequential data structure (like a vector or list):

  (db-atom \"db\" \"store\"
           :facts [[some db items] [some more items]]
           :meta metadata-map
           :validator validate-fn)

  These facts act as a default template for the pldb store. When a store is first
  created, it will be populated with this set of facts. On subsequent reloads,
  the pldb store will ignore these facts and use the previous database state
  instead."
  ;; HACK: facts shouldn't have to be a key, hard to get around that though
  ([db store & {:keys [meta validator facts]}]
   (let [a (DBAtom. nil meta validator nil db store nil)]
     (-pldb-load a facts)
     a)))

(defn add-facts!
  "Adds new facts and caches the new state."
  [a & facts]
  (-pldb-add! a facts))

(defn rm-facts!
  "Removes facts and stores the new state."
  [a & facts]
  (-pldb-remove! a facts))

(defn reset-facts!
  "Resets the logic db to a new set of facts and stores the new state"
  [a & facts]
  (-pldb-reset! a facts))

(defn clear!
  "Clears the atom and all associated data stores from clientside storage but
  keeps the clientside db. With the option ':delete true', the clientside db
  and all data will be permenantely deleted (making all connected db-atoms
  unsuable)."
  [a & {:keys [delete]}]
  (-pldb-clear! a delete))
