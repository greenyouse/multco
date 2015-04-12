(ns multco.core
  (:require [multco.storage :as s]
            [cljs.reader :as reader]
            [cljs.core.logic.pldb :as pldb]
            [cljs.core.async :refer [put! <! chan]]
            [fogus.datalog.bacwn]
            [fogus.datalog.bacwn.impl.database :as bacwn]
            [datascript :as dscript]
            ydn.db)
  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

(def ^:private trans-chan (chan))

(defn transactor
  "Transactor to cache the in-memory database asynchronously"
  []
  (go-loop []
    (let [[db store val] (<! trans-chan)]
      (try (s/clear-obj db store)
           (s/add-obj db store val)
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

(defn pldb-atom
  "This is a variation of the standard Clojure atom that holds a pldb database
  in memory and caches any pldb updates to clientside storage. The first
  argument is the name of the atom. The second argument is the database name
  you'll use (it's idiomatic to use your app's name and keep your app
  constrained to only one database). The third argument is the name of a pldb
  store and is used as the key name for storage.

  A new :facts keyword is used to define a template pldb database wrapped in a
  sequential data structure (like a vector or list):

  (pldb-atom \"app-name\" \"store\"
    :facts [[some db items] [some more items]]
    :meta metadata-map
    :validator validate-fn)

  These facts act as a default template for the pldb store. When a store is
  first created, it will be populated with this set of facts. On subsequent
  reloads, the pldb store will ignore these facts and use the previous
  database state instead."
  [db store & {:keys [meta validator facts]}]
  (let [new-db (apply pldb/db facts)
        atm (PldbAtom. nil meta validator nil db store)]
    (transactor)
    (s/atom-lookup db store atm new-db)
    atm))


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

(defn bacwn-atom
  "This is a variation of the standard Clojure atom that holds a bacwn database
  in memory and caches any bacwn updates to clientside storage. The first
  argument is the name of the atom. The second argument is the database name
  you'll use (it's idiomatic to use your app's name and keep your app
  constrained to only one database). The third argument is the name of a bacwn
  store and is used as the key name for storage. The fourth argument is a bacwn
  schema.

  A new :facts keyword is used to define a template bacwn database wrapped in a
  sequential data structure (like a vector or list):

  (bacwn-atom \"app-name\" \"store\" schema-name
    :facts [[some db items] [some more items]]
    :meta metadata-map
    :validator validate-fn)

  These facts act as a default template for the bacwn store. When a store is
  first created, it will be populated with this set of facts. On subsequent
  reloads, the bacwn store will ignore these facts and use the previous
  database state instead."
  [db store schema & {:keys [meta validator facts]}]
  (let [new-db (apply bacwn/add-tuples schema facts)
        atm (BacwnAtom. nil meta validator nil schema db store)]
    (transactor)
    (s/atom-lookup db store atm new-db)
    atm))


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

(defn datascript-atom
  "This is a variation of the standard Clojure atom that holds a datascript
  database in memory and caches any datascript updates to clientside storage.
  The first argument is the name of the atom. The second argument is the
  database name you'll use (it's idiomatic to use your app's name and keep
  your app constrained to only one database). The third argument is the name of
  a datascript store and is used as the key name for storage. The fourth
  argument is a datascript schema.

  A new :facts keyword is used to define a template datascript database
  wrapped in a sequential data structure (like a vector or list):

  (datascript-atom \"app-name\" \"store\" schema-name
    :facts [[some db items] [some more items]]
    :meta metadata-map
    :validator validate-fn)

  These facts act as a default template for the datascript store. When a store
  is first created, it will be populated with this set of facts. On subsequent
  reloads, the datascript store will ignore these facts and use the previous
  database state instead."
  [db store schema & {:keys [meta validator facts]}]
  (let [conn (dscript/create-conn schema)
        atm (DatascriptAtom. nil (assoc meta :listeners (atom {}))
              validator nil schema db store)]
    (transactor)
    (dscript/transact! conn facts)
    (s/atom-lookup db store atm @conn)
    atm))


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
  (s/clear-obj (.-db a) (.-store a)))
