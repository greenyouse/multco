# pldb-cache

This library is for persistent, clientside
[pldb](https://github.com/clojure/core.logic) databases.

This version is built on top of [ydn-db](http://dev.yathit.com/) but
sadly doesn't work quite yet. I wrote a temporary Web Storage
implementation but my plan is to remove it after completing a ydn-db
version (see the webstorage branch).  

## Installation

Add this dependency to your project.clj:
```clj
[com.greenyouse/pldb-cache "0.1.0-webstorage"]
```

## Usage

The main feature of this library is a `db-atom`, which holds a pldb
database in memory and caches any changes to it into clientside storage
(either IndexedDB, WebSQL, Web Storage, or UserData depending on what's
available). 

When your program starts, load the database using `db-atom`. This
will either load a given data template, if the  program database has
never been altered, or load the lastest state of your program. 

```clj
(defonce app-atom
   (p-cache/db-atom "app-db" "db-store" :facts
                    [[database code goes here]]))
```

This means persistent clientside storage for your app! The database is
loaded into memory and stored in an atom (`app-atom` in the above
example).Subsquent additions or retractions to your database will be
saved to the clientside storage and the in-memory database.

To add data use `add-facts!`:

```clj
(p-cache/add-facts! app-atom [some new fact(s)])
```

To remove data use `rm-facts!`:

```clj
(p-cache/rm-facts! app-atom [rm some fact(s)])
```

A database can also be reset using `reset-facts!`:

```clj
(p-cache/reset-facts! app-atom [new fact(s) to use])
```

If you need to delete things, `clear` will empty a database and `rm-db`
will remove a database from clientside storage.

```clj
(p-cache/clear db "my-app")

(p-cache/rm-db "my-db")
```

## Examples

Check the example folder for a fun demo.


## License

Copyright © 2015 greenyouse

Distributed under the [BSD 2-Clause License](http://www.opensource.org/licenses/BSD-2-Clause).
