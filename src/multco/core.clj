(ns multco.core)


;; FIXME: move these to setup fns within the atom type definitions
(defmacro pldb-atom
  "This is a variation of the standard Clojure atom that holds a pldb database
  in memory and caches any pldb updates to clientside storage. The first
  argument is the name of the atom. The second argument is the database name
  you'll use (it's idiomatic to use your app's name and keep your app constrained
  to only one database). The third argument is the name of a pldb store and is
  used as the key name for storage.

  A new :facts keyword is used to define a template pldb database wrapped in a
  sequential data structure (like a vector or list):

  (pldb-atom app-atom \"app\" \"store\"
    :facts [[some db items] [some more items]]
    :meta metadata-map
    :validator validate-fn)

  These facts act as a default template for the pldb store. When a store is
  first created, it will be populated with this set of facts. On subsequent
  reloads, the pldb store will ignore these facts and use the previous
  database state instead."
  [name db store & {:keys [meta validator facts]}]
  `(let [new-db# (apply cljs.core.logic.pldb/db ~facts)]
     (multco.core/transactor)
     (def ~name
       (multco.core/PldbAtom. nil ~meta ~validator nil ~db ~store))
     (multco.storage/atom-lookup ~db ~store ~name new-db#)))

(comment (clojure.pprint/pprint
            (macroexpand '(pldb-atom test-atom "test" "meow" :facts {:some "stuff"}))))


(defmacro bacwn-atom
  "This is a variation of the standard Clojure atom that holds a bacwn database
  in memory and caches any bacwn updates to clientside storage. The first
  argument is the name of the atom. The second argument is the database name
  you'll use (it's idiomatic to use your app's name and keep your app constrained
  to only one database). The third argument is the name of a bacwn store and is
  used as the key name for storage. The fourth argument is a bacwn schema.

  A new :facts keyword is used to define a template bacwn database wrapped in a
  sequential data structure (like a vector or list):

  (bacwn-atom app-atom \"app\" \"store\" schema-name
    :facts [[some db items] [some more items]]
    :meta metadata-map
    :validator validate-fn)

  These facts act as a default template for the bacwn store. When a store is
  first created, it will be populated with this set of facts. On subsequent
  reloads, the bacwn store will ignore these facts and use the previous
  database state instead."
  [name db store schema & {:keys [meta validator facts]}]
  `(let [new-db# (apply fogus.datalog.bacwn.impl.database/add-tuples ~schema ~facts)]
     (multco.core/transactor)
     (def ~name
       (multco.core/BacwnAtom. nil ~meta ~validator nil ~schema ~db ~store))
     (multco.storage/atom-lookup ~db ~store ~name new-db#)))

(comment (clojure.pprint/pprint
            (macroexpand '(bacwn-atom test-atom "test" "meow" {:schema ""} :facts {:some "stuff"}))))


(defmacro datascript-atom
  "This is a variation of the standard Clojure atom that holds a datascript
  database in memory and caches any datascript updates to clientside storage.
  The first argument is the name of the atom. The second argument is the database
  name you'll use (it's idiomatic to use your app's name and keep your app
  constrained to only one database). The third argument is the name of a datascript
  store and is used as the key name for storage. The fourth argument is a
  datascript schema.

  A new :facts keyword is used to define a template datascript database
  wrapped in a sequential data structure (like a vector or list):

  (db-atom \"store\" schema-name
  :facts [[some db items] [some more items]]
  :meta metadata-map
  :validator validate-fn)

  These facts act as a default template for the datascript store. When a store
  is first created, it will be populated with this set of facts. On subsequent
  reloads, the datascript store will ignore these facts and use the previous
  database state instead."
  [name db store schema & {:keys [meta validator facts]}]
  `(let [conn# (datascript/create-conn ~schema)
         report# (datascript/transact! conn# ~facts)]
     (multco.core/transactor)
     (def ~name
       (multco.core/DatascriptAtom. nil
         (assoc ~meta :listeners (atom {}))
         ~validator nil ~schema ~db ~store))
     (multco.storage/atom-lookup ~db ~store ~name @conn#)))

(comment (clojure.pprint/pprint
            (macroexpand '(datascript-atom test-atom "test" "meow" {:schema ""} :facts {:some "stuff"}))))
