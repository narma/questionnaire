(ns cdb.db
  (:require
   [datascript :as d]))


(def ^:private schema
  {
   ;:section/title {:db/valueType :db.type/string}

   :section/questions {:db/valueType :db.type/ref
                       :db/cardinality :db.cardinality/many}

   :question/options {:db/valueType :db.type/ref
                      :db/cardinality :db.cardinality/many}

   :question/options-selected {:db/valueType :db.type/ref
                               :db/cardinality :db.cardinality/many
                               }
   })

(def conn (d/create-conn schema))

(def form (atom 0))
(def client (atom 0))


;;;; Reactions

(def reactions (atom {}))

(defn listen-for! [key path fragments callback]
  (swap! reactions assoc-in (concat [path] fragments [key]) callback))

(defn unlisten-for! [key path fragments]
  (swap! reactions update-in (concat [path] fragments) dissoc key))


;; TODO: try to use d/q for support anything match
(d/listen! conn
  (fn [tx-data]
    (first (for [datom (:tx-data tx-data)
                 [path m] @reactions
                 :let [fragments ((apply juxt path) datom)]
                 [key callback] (get-in m fragments)]
             (do (prn "path: " path fragments)
               (callback datom tx-data key))))))


(d/pull @conn '[*] 1)
