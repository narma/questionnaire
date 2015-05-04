(ns cdb.app
  (:require-macros [cdb.util :refer [log]])
  (:require
   [cdb.db :refer [conn client form]]
   [cdb.view :refer [editpage]]
   [cdb.utils :refer [data->datoms]]
   [ajax.core :refer [GET POST]]
   [clojure.set :refer [intersection superset?]]
   [datascript :as d]
   [dommy.core :as dommy :refer-macros [sel1 sel]]
   rum))


(enable-console-print!)

(def el-root (.getElementById js/document "main_content"))

(defn rerender [db]
  (rum/mount (editpage db)
             el-root))

(defn get-section-handler
  [resp]
  (d/transact! conn (data->datoms "section" resp))
  (d/transact! conn
               (map (fn [id ] [:db/add (first id) :question/answer ""])
                    (d/q '[:find ?qid
                           :where [?qid :question/id]] @conn)))
  (rerender @conn))


;; (d/q '[:find ?e ?q :where [?e :question/title ?q]] @conn)

; (d/q '[:find ?e :where [?e :section/title]] @conn)

;; (d/q '[:find ?q ?a :where [?e :question/title ?q]
;;                      [?e :question/answer ?a]] @conn)


(defn ajax-error-handler
  [{:keys [status status-text]}]
  (.error js/console (str "something bad happened: " status " " status-text)))


;; (let [wattrs #{:section/title :question/title}
;;       iattrs #{:question/answer}
;;       rules [
;; ;;              #(not (empty?
;; ;;                     (intersection
;; ;;                      (set (map :a (:tx-data %)))
;; ;;                      wattrs))),
;; ;;              #(not (superset?
;; ;;                     iattrs
;; ;;                     (set (map :a (:tx-data %)))))

;;              #(-> (d/q '[:find (count ?q)
;;                          :in $
;;                          :where [?q :question/answer ?a]
;;                          [?q :question/answer ""]
;;                          ]
;;                        (:tx-data %))
;;                   empty?
;;                   not)
;;              ]
;;       ]
;;   (d/listen! conn
;;              (fn [tx-data]
;;                (when (some true? (map #(% tx-data) rules))
;;                  (rerender (:db-after tx-data))))))

; (rerender @conn)

;; (keys @last-td)




;; (empty? (d/q '[:find (count ?q)
;;                      :in $
;;                      :where [?q :question/answer ?a]
;;        [?q :question/answer ""]
;;                      ]
;;                    (:tx-data @last-td)))
; len is 1 and ?a is not empty
; len is 2 and one ?a is empty


(defn ^:export load-data
  [form-id c-id]
  (reset! form form-id)
  (reset! client c-id)
  (GET (str "/sections/" form-id)
       {:handler get-section-handler
        :error-handler ajax-error-handler}))


