# Multco

This library adds cross-platform persistence to existing ClojureScript
databases.

This version of multco only works over Web Storage (aka
localStorage). The goal, however, is to use the ydn-db polyfill to allow
multco to work over IndexedDB, WebSQL, Web Storage, and UserData. Any
contributions are welcome! 

Right now it covers:
* [pldb](https://github.com/clojure/core.logic) 
* [bacwn](https://github.com/fogus/bacwn)
* [datascript](https://github.com/tonsky/datascript)

## Installation

Add this dependency to your project.clj:
```clj
[com.greenyouse/multco "0.1.1-webstorage]
```

## Usage

The main feature of this library is a `db-atom`, which can hold either a
pldb, bacwn, or datascript database in memory and cache any db changes
into clientside storage (either IndexedDB, WebSQL, Web Storage, or
UserData depending on what's available). There are three types of
db-atoms that match their library names: 

* `pldb-atom`
* `bacwn-atom`
* `datascript-atom`

When your program starts, load the database using one of these
db-atoms. This will either load a given data template, if the program
database has never been altered, or reload the lastest state of your
program.   


```clj
(def app-atom
  (multco/pldb-atom "my-db" :facts 
    [[example foo bar]]))
```

This means persistent clientside storage for your app! The database is
loaded into memory and stored in a special variation of an atom
(`app-atom` in the above example).Subsequent additions or retractions to
your database will be saved to the client-side storage and the in-memory
database. 

To add data use `add-facts!`:

```clj
(multco/add-facts! app-atom [some new fact(s)])
```

To remove data use `remove-facts!`:

```clj
(multco/remove-facts! app-atom [rm some fact(s)])
```

A database can also be reset using `reset-facts!`:

```clj
(multco/reset-facts! app-atom [new fact(s) to use])
```

If you need to delete things, `clear` will empty a database and `rm-db`
will remove a database from clientside storage.

```clj
(multco/clear app-atom)

(multco/rm-db app-atom)
```

## Examples

Check the example folder for fun demos that cover each library.


## License

Copyright © 2015 greenyouse

Distributed under the [BSD 2-Clause License](http://www.opensource.org/licenses/BSD-2-Clause).
