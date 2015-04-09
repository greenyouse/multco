(ns examples.star-wars.datascript
  (:require [cljs.core :as c] ;crazy repl issues because of datscript namespacing
            [datascript :as d]
            [multco.core :as m]))

;; Do a datascript schema like normal.
(def schema
  {:person/name {}
   :person/ancestor {:db/cardinality :db.cardinality/many
                     :db/valueType :db.type/ref}
   :person/children {:db/cardinality :db.cardinality/many
                     :db/valueType :db.type/ref}
   :person/gender {}})

;; This serves as a default set of datums. Any subsequent additions
;; or retractions will be saved and override this set of datums.
;; This allows a program to store its info persistently!
;; For reference: http://www.chartgeek.com/star-wars-family-tree/
;; (it takes the place of datascript's conn)
(m/datascript-atom test-atom "test" "datascript-test" schema :facts
  [{:db/id 1
    :person/name "Shmi Skywalker"
    :person/children 9 ;"Anakin Skywalker"
    :person/gender "female"}
   {:db/id 2
    :person/name "Cliegg Lars"
    :person/children 5 ;"Owen Lars"
    :person/gender "male"}
   {:db/id 3
    :person/name "Ruwee Naberrie"
    :person/children 9 ;"Padme Amadala"
    :person/gender "male"}
   {:db/id 4
    :person/name "Jobal Naberrie"
    :person/children 9 ;"Padme Amidala"
    :person/gender "female"}
   {:db/id 5
    :person/name "Owen Lars"
    :person/ancestor 2; "Cliegg Lars"
    :person/children 11; "Luke Skywalker"
    :person/gender "male"}
   {:db/id 6
    :person/name "Beru Lars"
    :person/children 11; "Luke Skywalker"
    :person/gender "female"}
   {:db/id 7
    :person/name "Bail Organa"
    :person/children 13; "Princess Leia"
    :person/gender "male"}
   {:db/id 8
    :person/name "Breha Organa"
    :person/children 13; "Princess Leia"
    :person/gender "female"}
   {:db/id 9
    :person/name "Anakin Skywalker"
    :person/ancestor 1; "Shmi Skywalker"
    :person/children [11 13]
    :person/gender "male"}
   {:db/id 10
    :person/name "Padme Amadala"
    :person/ancestor [3 4]
    :person/children [11 13]
    :person/gender "female"}
   {:db/id 11
    :person/name "Luke Skywalker"
    :person/ancestor [5 6 9 10]
    :person/children 15; "Ben Skywalker"
    :person/gender "male"}
   {:db/id 12
    :person/name "Mara Jade"
    :person/children 15; "Ben Skywalker"
    :person/gender "female"}
   {:db/id 13
    :person/name "Princess Leia"
    :person/ancestor [7 8 9 10]
    :person/children [16 17 18]
    :person/gender "female"}
   {:db/id 14
    :person/name "Han Solo"
    :person/children [16 17 18]
    :person/gender "male"}
   {:db/id 15
    :person/name "Ben Skywalker"
    :person/ancestor [11 12]
    :person/gender "male"}
   {:db/id 16
    :person/name "Jaina Solo"
    :person/ancestor [13 14]
    :person/gender "female"}
   {:db/id 17
    :person/name "Jacen Solo"
    :person/ancestor [13 14]
    :person/gender "male"}
   {:db/id 18
    :person/name "Anakin Solo"
    :person/ancestor [13 14]
    :person/gender "male"}])


;; Find Luke's father + step-dad
(c/println (d/q '[:find ?dad
                  :where
                  [?e1 :person/name "Luke Skywalker"]
                  [?e2 :person/children ?e1]
                  [?e2 :person/name ?dad]
                  [?e2 :person/gender "male"]]
             @test-atom))

;; Find Luke's mom (step-mom too)
(c/println (d/q '[:find ?step-mom
                  :where
                  [?e1 :person/name "Luke Skywalker"]
                  [?e2 :person/children ?e1]
                  [?e2 :person/name ?step-mom]
                  [?e2 :person/gender "female"]]
             @test-atom))

;; FIXME: correct the other examples here
;; Luke has one grandpa + a step-grandpa
(c/println (d/q '[:find ?grandpa
                  :where
                  [?e1 :person/name "Luke Skywalker"]
                  [?e2 :person/children ?e1]
                  [?e2 :person/ancestor ?e3]
                  [?e3 :person/name ?grandpa]
                  [?e3 :person/gender "male"]]
             @test-atom))

;; let's find all the father-children relations
(c/println (d/q '[:find ?father ?child
                  :where
                  [?e1 :person/name ?child]
                  [?e1 :person/gender "male"]
                  [?e2 :person/children ?e1]
                  [?e2 :person/name ?father]
                  [?e2 :person/gender "male"]]
             @test-atom))


;; Now let's add more datums to our database and see how the changes are
;; reflected in the queries
(m/add-facts! test-atom
  {:db/id 19
   :person/name "Chewbacca"
   :person/ancestor 20}
  {:db/id 9
   :person/ancestor 20}
  {:db/id 20
   :person/name "???"
   :person/children [19 9]}) ; chewie and anakin

;; So now we have unknown fathers in our data
(c/println (d/q '[:find ?dad ?child
                  :where
                  [?e1 :person/name ?child]
                  [?e2 :person/children ?e1]
                  [?e2 :person/name ?dad]
                  [?e2 :person/name "???"]]
             @test-atom))

;; Let's get rid of that and go back to the default
(m/rm-facts! test-atom
  [:db.fn/retractEntity 19]
  [:db.fn/retractEntity 20])

;; Double-check that the datums are gone
(c/println (d/q '[:find ?dad ?child
                  :where
                  [?e1 :person/name ?child]
                  [?e2 :person/children ?e1]
                  [?e2 :person/name ?dad]
                  [?e2 :person/name "???"]]
             @test-atom))


;; What if we only care about one relationship and want to delete
;; everything else? Just reset the database:
(m/reset-facts! test-atom
  {:db/id 21
   :person/name "???"
   :person/children 22}
  {:db/id 22
   :person/name "Jar Jar Binks"
   :person/gender "male"})

;; Try reloading the page and evaluting test-atom and the relations above
;; it. Now check to see if the change we made persisted.
;; Is Jar Jar really the only thing we have?
(c/println (d/q '[:find ?ppl
                  :where
                  [?e1 :person/name ?ppl]]
             @test-atom))


;; Yup, Jar Jar is the only thing in there (oh no) and everything worked!
;; If you want to delete this experiment do:
(m/clear! test-atom)

;; and here's how to delete the entire database:
(m/rm-db test-atom)
