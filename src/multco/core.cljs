(ns multco.core
  (:require [multco.storage :as s]
            [cljs.reader :as reader]
            [cljs.core.logic.pldb :as pldb]
            [cljs.core.async :refer [put! <! chan]]
            [fogus.datalog.bacwn]
            [fogus.datalog.bacwn.impl.database :as bacwn]
            [datascript :as dscript]
            ydn.db)
  (:require-macros [cljs.core.async.macros :refer [go-loop]]
                   [multco.core]))

(def ^:private trans-chan (chan))

(defn transactor
  "Transactor to cache the in-memory database asynchronously"
  []
  (go-loop []
    (let [[db store val] (<! trans-chan)]
      (try (s/clear-db db store)
           (s/add-db db store val)
           (catch js/Error e
             (.log js/console "Multco Error: improper message to transactor")))
      (recur))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; pldb

(defprotocol IDatabase
  (-db-add! [this facts])
  (-db-remove! [this facts])
  (-db-reset! [this facts]))

(deftype PldbAtom [^:mutable state meta validator watches
                   ^string db ^string store]

  IDatabase
  (-db-add! [this facts]
    (let [new-db (apply pldb/db-facts state facts)]
      (put! trans-chan [db store new-db])
      (-reset! this new-db)))
  (-db-remove! [this facts]
    (let [new-db (apply pldb/db-retractions state facts)]
      (put! trans-chan [db store new-db])
      (-reset! this new-db)))
  (-db-reset! [this facts]
    (let [new-db (apply pldb/db facts)]
      (put! trans-chan [db store new-db])
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
    (-write writer "#<Pldb-Atom: ")
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


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Bacwn

(defn remove-tuples
  "Removes a collection of tuples from the db, as
  (remove-tuples db
    [:rel-name :key-1 1 :key-2 2]
    [:rel-name :key-1 2 :key-2 3])"
  [db & tupls]
  (reduce #(bacwn/remove-tuple % (first %2) (apply hash-map (next %2))) db tupls))

(deftype BacwnAtom [^:mutable state meta validator watches schema
                    ^string db ^string store]

  IDatabase
  (-db-add! [this facts]
    (let [new-db (apply bacwn/add-tuples state facts)]
      (put! trans-chan [db store new-db])
      (-reset! this new-db)))
  (-db-remove! [this facts]
    (let [new-db (apply remove-tuples state facts)]
      (put! trans-chan [db store new-db])
      (-reset! this new-db)))
  (-db-reset! [this facts]
    (let [new-db (apply bacwn/add-tuples schema facts)]
      (put! trans-chan [db store new-db])
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


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Datascript

(deftype DatascriptAtom [^:mutable state meta validator watches schema
                         ^string db ^string store]

  IDatabase
  (-db-add! [this facts]
    (let [report (dscript/transact! (atom state :meta meta) facts)
          new-db (:db-after report)]
      (put! trans-chan [db store new-db])
      (-reset! this  new-db)))
  ;; I'm assuming you'll use :db/retract or :db.fn/retract
  (-db-remove! [this facts]
    (-db-add! this facts))
  (-db-reset! [this facts]
    (let [report (-> schema dscript/empty-db (atom :meta meta)
                   (dscript/transact! facts))
          new-db (:db-after report)]
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
    (-write writer "#<Datascript-Atom: ")
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
  storage."
  [a]
  (reset! a nil)
  (s/clear-db (.-db a) (.-store a)))
