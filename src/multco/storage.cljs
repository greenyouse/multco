(ns multco.storage)

;; clear, getItem, key, removeItem, setItem
(defn add-db
  [store value]
  (js/window.localStorage.setItem store value))

(defn rm-db
  []
  (js/window.localStorage.clear))

(defn clear-db
  [store]
  (js/window.localStorage.removeItem store))

(defn get-db
  [store]
  (js/window.localStorage.getItem store))
