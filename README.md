# Multco

This library is for persistent, clientside
[pldb](https://github.com/clojure/core.logic) databases.

This version of multco only works over Web Storage (aka
localStorage). Hopefully the ydn-db branch can be finished to allow
multco to work over IndexedDB, WebSQL, Web Storage, and
UserData. Any contributions are welcome!

## Installation

Add this dependency to your project.clj:
```clj
[com.greenyouse/multco "0.1.0-webstorage]
```

## Usage

Before doing anything, declare a new clientside database using `setup`:

```clj
(def db
  (multco/setup "my-db"))
```

When your program starts, load the database using `init-facts`. This
will either load a given data template, if the  program database has
never been altered, or load the lastest stored state of your program. 

```clj
(defonce some-data-template
   (multco/init-facts app-db "my-app" app-atom
                       [database code goes here]))
```

This means persistent clientside storage for your app! The database is
loaded into memory and stored in an atom (`app-atom` in the above
example).Subsquent additions or retractions to your database will be
saved to the clientside storage and the in-memory database.

To add data use `add-facts!`:

```clj
(multco/add-facts! db "my-app" app-atom [some new fact(s)])
```

To remove data use `remove-facts!`:

```clj
(multco/remove-facts! db "my-app" app-atom [rm some fact(s)])
```

A database can also be reset using `reset-facts!`:

```clj
(multco/reset-facts! db "my-app" app-atom [new fact(s) to use])
```

If you need to delete things, `clear` will empty a database and `rm-db`
will remove a database from clientside storage.

```clj
(multco/clear db "my-app")

(multco/rm-db "my-db")
```

## Examples

Check the example folder for a fun demo.


## License

Copyright © 2015 greenyouse

Distributed under the [BSD 2-Clause License](http://www.opensource.org/licenses/BSD-2-Clause).
