(ns cdb.utils
  (:require [datascript :as d]))


(defn data->datoms [prefix json]
  (let [make-prefix
        (fn [p]
          (let [[full short]
                (re-matches #"(.+?)s?$" p)]
            short))]
    (loop [current json
           data-rest []
           ctx [{:prefix (make-prefix prefix)
                 :id (d/tempid :db.part/user)}]
           acc []]
      (cond
       (and (empty? current) (empty? data-rest))
       acc

       (empty? current)
       (recur (first data-rest) (rest data-rest)
              (if (sequential? current)
                (pop ctx)
                (conj (pop ctx) (assoc (peek ctx) :id (d/tempid :db.part/user))))
              (if (and (map? current)
                       (-> (count ctx) (> 1)))
                (let [ictx (peek ctx)
                      pctx
                      (nth ctx (- (count ctx) 2))]
                  (conj acc [:db/add
                             (:id pctx)
                             (-> (:prefix pctx)
                                 (str "/" (:prefix ictx) "s")
                                 keyword)
                             (:id ictx)]))
                acc))

       (map? current)
       (let [k (first (keys current))
             v (get current k)
             ictx (peek ctx)]
         (if (sequential? v)
           (recur v
                  (conj data-rest (dissoc current k))
                  (conj ctx {:id (d/tempid :db.part/user)
                             :prefix (make-prefix k)
                             })
                  acc)
           (recur (dissoc current k)
                  data-rest
                  ctx
                  (conj acc [:db/add (:id ictx)
                             (keyword (str (:prefix ictx) "/" k))
                             v]))))

       (sequential? current)
       (recur (first current)
              (cons (rest current) data-rest)
              ctx
              acc)))))
