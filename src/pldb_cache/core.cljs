(ns pldb-cache.core
  (:require [pldb-cache.storage :as s]
            [cljs.reader :as reader]
            [cljs.core.logic.pldb :as pldb]
            [cljs.core.async :as async :refer [put! <! chan]])
  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

(def ^:private trans-chan (chan))

(defn transactor
  "Transactor to cache the in-memory database asynchronously"
  []
  (go-loop []
    (let [[store val] (<! trans-chan)]
      (try (s/clear-db store)
           (s/add-db store val)
           (catch js/Error e
             (.log js/console "pldb-cache Error: improper message to transactor")))
      (recur))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; DB Atom

(defprotocol IDatabase
  (-pldb-add! [this facts])
  (-pldb-remove! [this facts])
  (-pldb-reset! [this facts]))

(deftype DBAtom [^:mutable state meta validator watches ^string store]

  IDatabase
  (-pldb-add! [this facts]
    (let [new-db (apply pldb/db-facts state facts)]
      (put! trans-chan [store new-db])
      (-reset! this new-db)))
  (-pldb-remove! [this facts]
    (let [new-db (apply pldb/db-retractions state facts)]
      (put! trans-chan [store new-db])
      (-reset! this new-db)))
  (-pldb-reset! [this facts]
    (let [new-db (apply pldb/db facts)]
      (put! trans-chan [store new-db])
      (-reset! this new-db)))

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
  in memory and caches any pldb updates to clientside storage. The first argument
  is the name of a pldb store and is used as the key name for localStorage.

  A new :facts keyword is used to define a template pldb database wrapped in a
  sequential data structure (like a vector or list):

  (db-atom \"store\"
           :facts [[some db items] [some more items]]
           :meta metadata-map
           :validator validate-fn)

  These facts act as a default template for the pldb store. When a store is first
  created, it will be populated with this set of facts. On subsequent reloads,
  the pldb store will ignore these facts and use the previous database state
  instead."
  ;; HACK: facts shouldn't have to be a key, hard to get around that though
  [store & {:keys [meta validator facts]}]
  (transactor)
  (let [state (if (s/get-db store)
                (reader/read-string (s/get-db store))
                (let [new-db (apply pldb/db facts)]
                  (put! trans-chan [store new-db])
                  new-db))]
    (DBAtom. state meta validator nil store)))

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
  "Resets the data to an empty pldb database"
  [a]
  (-pldb-reset! a nil))

(defn rm-db
  "Removes all clientside data (meant only for things like uninstalling a program)."
  []
  (s/rm-db))
