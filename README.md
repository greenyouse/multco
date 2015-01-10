# pldb-cache

This library is for persistent, clientside
[pldb](https://github.com/clojure/core.logic) databases.

## Installation

Add this dependency to your project.clj:
```clj
[com.greenyouse/pldb-cache "0.1.0"]
```

## Usage

Before doing anything, declare a new clientside database using `setup`:

```clj
(def db
  (p-cache/setup "my-db" {:stores [{:name "database"
                                  :keyPath "name"
                                  :indexes [{:keyPath "value"
                                             :type "TEXT"}]}]}))
```

The schema above should be used for all your `pldb-cache` projects.

When your program starts, load the database using `init-facts`. This
will either load a given data template, if the  program database has
never been altered, or load the lastest stored state of your program. 

```clj
(defonce some-data-template
   (p-cache/init-facts app-db "my-app" app-atom
     (pldb/db
      [database code goes here])))
```

This means persistent clientside storage for your app! The database is
loaded into memory and stored in an atom (`app-atom` in the above
example).Subsquent additions or retractions to your database will be
saved to the clientside storage and the in-memory database.

To add data use `add-facts!`:

```clj
(p-cache/add-facts! db "my-app" app-atom [some new fact(s)])
```

To remove data use `remove-facts!`:

```clj
(p-cache/remove-facts! db "my-app" app-atom [rm some fact(s)])
```


## Examples

Check the example folder for a demo.


## License

Copyright © 2015 greenyouse

Distributed under the [BSD 2-Clause License](http://www.opensource.org/licenses/BSD-2-Clause).
