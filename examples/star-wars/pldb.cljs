(ns examples.star-wars.pldb
  (:require [multco.core :as m]
            [cljs.core.logic.pldb :as pldb]
            [cljs.core.logic :as l]))

(pldb/db-rel parent Parent Child)
(pldb/db-rel female Person)
(pldb/db-rel male Person)

;; This serves as a default set of facts. Any subsequent additions
;; or retractions will be saved and override this set of facts.
;; This allows a program to store its info persistently!
;; For reference: http://www.chartgeek.com/star-wars-family-tree/
(def test-atom
  (m/pldb-atom "test" "pldb-test" :facts
    [[parent "Shmi Skywalker" "Anakin Skywalker"]
     [parent "Ruwee Naberrie" "Padme Amidala"]
     [parent "Jorbal Naberrie" "Padme Amidala"]
     [parent "Cliegg Lars" "Owen Lars"]
     [parent "Owen Lars" "Luke Skywalker"]
     [parent "Beru Lars" "Luke Skywalker"]
     [parent "Luke Skywalker" "Ben Skywalker"]
     [parent "Mara Jade" "Ben Skywalker"]
     [parent "Anakin Skywalker" "Luke Skywalker"]
     [parent "Padme Amidala" "Luke Skywalker"]
     [parent "Anakin Skywalker" "Princess Leia"]
     [parent "Padme Amidala" "Princess Leia"]
     [parent "Bail Organa" "Princess Leia"]
     [parent "Breha Organa" "Princess Leia"]
     [parent "Princess Leia" "Jaina Solo"]
     [parent "Princess Leia" "Jacen Solo"]
     [parent "Princess Leia" "Anakin Solo"]
     [parent "Han Solo" "Jaina Solo"]
     [parent "Han Solo" "Jacen Solo"]
     [parent "Han Solo" "Anakin Solo"]

     [female "Shmi Skywalker"]
     [female "Jorbal Naberrie"]
     [female "Beru Lars"]
     [female "Mara Jade"]
     [female "Padme Amidala"]
     [female "Breha Organa"]
     [female "Princess Leia"]
     [female "Jaina Solo"]

     [male "Cliegg Lars"]
     [male "Owen Lars"]
     [male "Ruwee Naberrie"]
     [male "Anakin Skywalker"]
     [male "Bail Organa"]
     [male "Ben Skywalker"]
     [male "Han Solo"]
     [male "Jacen Solo"]
     [male "Anakin Solo"]]))


;; Find Luke's father (he has two because of adoption)
(println (pldb/with-db @test-atom
           (l/run* [q]
             (parent q "Luke Skywalker")
             (male q))))

;; Find Luke's mom (step-mom too)
(println (pldb/with-db @test-atom
           (l/run* [q]
             (parent q "Luke Skywalker")
             (female q))))

;; Luke's grandmothers
(println (pldb/with-db @test-atom
           (l/run* [q]
             (l/fresh [p]
               (parent p "Luke Skywalker")
               (parent q p)
               (female q)))))

;; let's find all the father-child relations
(println (pldb/with-db @test-atom
           (l/run* [q]
             (l/fresh [p s]
               (parent p s)
               (male p)
               (l/== q [p s])))))


;; Now let's add more facts to our database and see how the changes are
;; reflected in the queries
(m/add-facts! test-atom
  [parent "???" "Chewbacca"]
  [parent "???" "Anakin Skywalker"]
  [male "???"]
  [male "Chewbacca"])

;; So now we have people with unknown fathers in our data
(println (pldb/with-db @test-atom
           (l/run* [q]
             (parent "???" q))))


;; Let's get rid of that and go back to the default
(m/rm-facts! test-atom
  [parent "???" "Chewbacca"]
  [parent "???" "Anakin Skywalker"]
  [male "???"]
  [male "Chewbacca"])

;; Double-check that the facts are gone
(println (pldb/with-db @test-atom
           (l/run* [q]
             (parent "???" q))))


;; What if we only care about one relationship and want to delete
;; everything else? Just reset the database:
(m/reset-facts! test-atom
  [parent "???" "Jar Jar Binks"]
  [female "???"]
  [male "???"]
  [male "Jar Jar Binks"])


;; Try reloading the page and evaluting test-atom and the relations above
;; it. Now check to see if the change we made persisted.
(println (pldb/with-db @test-atom
           (l/run* [q]
             (male "Jar Jar Binks"))))

;; Is Jar Jar really the only thing we have?
(println (pldb/with-db @test-atom
           (l/run* [q]
             (l/fresh [p s]
               (parent p s)
               (l/== q [p s])))))

;; Yup, Jar Jar is the only thing in there (oh no) and everything worked!
;; If you want to delete this experiment do:
(m/clear! test-atom)

;; and here's how to delete the entire database:
(m/rm-db test-atom)
