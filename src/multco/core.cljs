(ns multco.core
  (:require [multco.storage :as s]
            [cljs.reader :as reader]
            [cljs.core.logic.pldb :as pldb]
            [cljs.core.async :as async :refer [put! <! chan]]
            [fogus.datalog.bacwn]
            [fogus.datalog.bacwn.impl.database :as bacwn])
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
             (.log js/console "Multco cache Error: improper message to transactor")))
      (recur))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; pldb

(defprotocol IDatabase
  (-db-add! [this facts])
  (-db-remove! [this facts])
  (-db-reset! [this facts]))

(deftype PldbAtom [^:mutable state meta validator watches ^string store]

  IDatabase
  (-db-add! [this facts]
    (let [new-db (apply pldb/db-facts state facts)]
      (put! trans-chan [store new-db])
      (-reset! this new-db)))
  (-db-remove! [this facts]
    (let [new-db (apply pldb/db-retractions state facts)]
      (put! trans-chan [store new-db])
      (-reset! this new-db)))
  (-db-reset! [this facts]
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

(defn pldb-atom
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
  [store & {:keys [meta validator facts]}]
  (transactor)
  (let [state (if (s/get-db store)
                (reader/read-string (s/get-db store))
                (let [new-db (apply pldb/db facts)]
                  (put! trans-chan [store new-db])
                  new-db))]
    (PldbAtom. state meta validator nil store)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Bacwn

(defn remove-tuples
  "Removes a collection of tuples from the db, as
   (remove-tuples db
      [:rel-name :key-1 1 :key-2 2]
      [:rel-name :key-1 2 :key-2 3])"
  [db & tupls]
  (reduce #(bacwn/remove-tuple % (first %2) (apply hash-map (next %2))) db tupls))

(deftype BacwnAtom [^:mutable state meta validator watches schema ^string store]

  IDatabase
  (-db-add! [this facts]
    (let [new-db (apply bacwn/add-tuples state facts)]
      (put! trans-chan [store new-db])
      (-reset! this new-db)))
  (-db-remove! [this facts]
    (let [new-db (apply remove-tuples state facts)]
      (put! trans-chan [store new-db])
      (-reset! this new-db)))
  (-db-reset! [this facts]
    (let [new-db (apply bacwn/add-tuples schema facts)]
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
    (-write writer "#<Bacwn-Atom: ")
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

(defn bacwn-atom
  "This is a variation of the standard Clojure atom that holds a bacwn database
  in memory and caches any bacwn updates to clientside storage. The first argument
  is the name of a bacwn store and is used as the key name for localStorage.

  A new :facts keyword is used to define a template bacwn database wrapped in a
  sequential data structure (like a vector or list):

  (db-atom \"store\"
           :facts [[some db items] [some more items]]
           :meta metadata-map
           :validator validate-fn)

  These facts act as a default template for the bacwn store. When a store is first
  created, it will be populated with this set of facts. On subsequent reloads,
  the bacwn store will ignore these facts and use the previous database state
  instead."
  [store schema & {:keys [meta validator facts]}]
  (transactor)
  (let [state (if (s/get-db store)
                (reader/read-string (s/get-db store))
                (let [new-db (apply bacwn/add-tuples schema facts)]
                  (put! trans-chan [store new-db])
                  new-db))]
    (BacwnAtom. state meta validator nil schema store)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; General functions

(defn add-facts!
  "Adds new facts and caches the new state."
  [a & facts]
  (-db-add! a facts))

(defn rm-facts!
  "Removes facts and stores the new state."
  [a & facts]
  (-db-remove! a facts))

(defn reset-facts!
  "Resets the logic db to a new set of facts and stores the new state"
  [a & facts]
  (-db-reset! a facts))

(defn clear!
  "Resets the data to an empty database."
  [a]
  (-db-reset! a nil))

(defn rm-db
  "Resets the data to an empty database and removes the database from
  localStorage."
  [a]
  (reset! a nil)
  (s/clear-db (.-store a)))
