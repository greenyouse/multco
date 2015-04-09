(ns examples.star-wars.bacwn
  (:use-macros [fogus.datalog.bacwn.macros :only [<- ?- make-database]])
  (:require [fogus.datalog.bacwn :as bacwn]
            [fogus.datalog.bacwn.impl.rules :as r]
            [multco.core :as m]))

;; Do a bacwn schema like normal.
(def schema
  (make-database
    (relation :parent [:ancestor :child])
    (index :parent :child)

    (relation :male [:person])
    (index :male :person)

    (relation :female [:person])
    (index :female :person)))

;; This serves as a default set of datums. Any subsequent additions
;; or retractions will be saved and override this set of datums.
;; This allows a program to store its info persistently!
;; For reference: http://www.chartgeek.com/star-wars-family-tree/
(m/bacwn-atom test-atom "test" "bacwn-test" schema :facts
  [[:parent :ancestor "Shmi Skywalker" :child "Anakin Skywalker"]
   [:parent :ancestor "Ruwee Naberrie" :child "Padme Amidala"]
   [:parent :ancestor "Jorbal Naberrie" :child "Padme Amidala"]
   [:parent :ancestor "Cliegg Lars" :child "Owen Lars"]
   [:parent :ancestor "Ownen Lars" :child "Luke Skywalker"]
   [:parent :ancestor "Beru Lars" :child "Luke Skywalker"]
   [:parent :ancestor "Luke Skywalker" :child "Ben Skywalker"]
   [:parent :ancestor "Mara Jade" :child "Ben Skywalker"]
   [:parent :ancestor "Anakin Skywalker" :child "Luke Skywalker"]
   [:parent :ancestor "Padme Amidala" :child "Luke Skywalker"]
   [:parent :ancestor "Anakin Skywalker" :child "Princess Leia"]
   [:parent :ancestor "Padme Amidala" :child "Princess Leia"]
   [:parent :ancestor "Bail Organa" :child "Princess Leia"]
   [:parent :ancestor "Breha Organa" :child "Princess Leia"]
   [:parent :ancestor "Princess Leia" :child "Jaina Solo"]
   [:parent :ancestor "Princess Leia" :child "Jacen Solo"]
   [:parent :ancestor "Princess Leia" :child "Anakin Solo"]
   [:parent :ancestor "Han Solo" :child "Jaina Solo"]
   [:parent :ancestor "Han Solo" :child "Jacen Solo"]
   [:parent :ancestor "Han Solo" :child "Anakin Solo"]

   [:female :person "Shmi Skywalker"]
   [:female :person "Jorbal Naberrie"]
   [:female :person "Beru Lars"]
   [:female :person "Mara Jade"]
   [:female :person "Padme Amidala"]
   [:female :person "Breha Organa"]
   [:female :person "Princess Leia"]
   [:female :person "Jaina Solo"]

   [:male :person "Cliegg Lars"]
   [:male :person "Owen Lars"]
   [:male :person "Ruwee Naberrie"]
   [:male :person "Anakin Skywalker"]
   [:male :person "Bail Organa"]
   [:male :person "Ben Skywalker"]
   [:male :person "Han Solo"]
   [:male :person "Jacen Solo"]
   [:male :person "Anakin Solo"]])

;; The rest is normal bacwn.
(def rules
  (r/rules-set
    (<- (:father :dad ?x :child ?y)
      (:parent :ancestor ?x :child ?y)
      (:male :person ?x))
    (<- (:mother :mom ?x :child ?y)
      (:parent :ancestor ?x :child ?y)
      (:female :person ?x))
    (<- (:grandpa :gramps ?x :grandchild ?y)
      (:parent :ancestor ?z :child ?y)
      (:parent :ancestor ?x :child ?z)
      (:male :person ?x))))

(def wp-1 (bacwn/build-work-plan rules (?- :father :dad ?x :child '??name)))
(def wp-2 (bacwn/build-work-plan rules (?- :mother :mom ?x :child '??name)))
(def wp-3 (bacwn/build-work-plan rules (?- :grandfather :gramps ?x :grandchild '??name)))

;; Find Luke's father
(println (bacwn/run-work-plan wp-1 @test-atom {'??name "Luke Skywalker"}))

;; Find Luke's mom (step-mom too)
(println (bacwn/run-work-plan wp-2 @test-atom {'??name "Luke Skywalker"}))

;; FIXME: but there is an error here because he has none!
;; Luke has only one grandpa (not an error)
(println (bacwn/run-work-plan wp-3 @test-atom {'??name "Luke Skywalker"}))

;; another way to do queries, let's find all the father-child relations
(println (bacwn/q (?- :father :dad ?x :child ?y)
           @test-atom
           rules
           {}))


;; Now let's add more datums to our database and see how the changes are
;; reflected in the queries
(m/add-facts! test-atom
  [:parent :ancestor "???" :child "Chewbacca"]
  [:parent :ancestor "???" :child "Anakin Skywalker"]
  [:male :person "???"]
  [:male :person "Chewbacca"])

;; So now we have unknown fathers in our data
(println (bacwn/q (?- :father :dad "???" :child ?y)
            @test-atom
            rules
            {}))

;; Let's get rid of that and go back to the default
(m/rm-facts! test-atom
  [:parent :ancestor "???" :child "Chewbacca"]
  [:parent :ancestor "???" :child "Anakin Skywalker"]
  [:male :person "???"]
  [:male :person "Chewbacca"])

;; Double-check that the datums are gone
(println (bacwn/q (?- :father :dad "???" :child ?y)
            @test-atom
            rules
            {}))


;; What if we only care about one relationship and want to delete
;; everything else? Just reset the database:
(m/reset-facts! test-atom
  [:parent :ancestor "???" :child "Jar Jar Binks"]
  [:female :person "???"]
  [:male :person "???"]
  [:male :person "Jar Jar Binks"])


;; Try reloading the page and evaluting test-atom and the relations above
;; it. Now check to see if the change we made persisted.
;; Is Jar Jar really the only thing we have?
(println (bacwn/q (?- :father :dad "???" :child ?y)
            @test-atom
            rules
            {}))

;; Yup, Jar Jar is the only thing in there (oh no) and everything worked!
;; If you want to delete this experiment do:
(m/clear! test-atom)

;; and here's how to delete the entire database:
(m/rm-db test-atom)
