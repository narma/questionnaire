(ns cdb.view
  (:require-macros [cdb.util :refer [log]])
  (:require
   [clojure.string :as str]
   [datascript :as d]
   [ajax.core :refer [GET POST]]
   [dommy.core :as dommy :refer-macros [sel1 sel]]
   rum
   [cdb.db :refer [conn client form listen-for! unlisten-for!]]))

(enable-console-print!)

(def active-section (atom nil))
(declare rerender)

;;; Datomic mixins

(defn listen-for-mixin [path-fn]
  {
   :transfer-state
   (fn [old new]
     (assoc new ::listen-path (::listen-path old)))
   :did-mount
    (fn [state]
      (let [[args path] (apply path-fn (:rum/args state))
            key         (rand)
            comp        (:rum/react-component state)
            callback    (fn [datom tx-data key] (rum/request-render comp))]
        (listen-for! key args path callback)
        (assoc state ::listen-path [key args path])))
    :will-unmount
    (fn [state]
      (apply unlisten-for! (::listen-path state))) })


(def question-mixin (listen-for-mixin
                     (fn [qid] [[:e] [qid]])))

(def section-data (listen-for-mixin
                     (fn [sid] [[:a :v] [:question/_section sid]])))


;;;

;; react components
(def complete?-rules '[[(complete? ?nempty ?qid)
               [?qid :question/answer ?answer]
               [(?nempty ?answer)]
               ]
              [(complete? ?nempty ?qid)
               [?qid :question/options-selected ?sel]]
              [(complete? ?nempty ?qid)
               [?qid :question/mark-complete true]]
              ])

(defn questions-done
  []
  (->> (d/q '[:find ?qid
              :in $ % ?nempty
              :where
              [?qid :question/id]
              (complete? ?nempty ?qid)
              ]
            @conn
            complete?-rules
            #(not (empty? %))
            )
       (map first)))


(def drag-mixin
  {:did-mount
   (fn [state] (let [compn (:rum/react-component state)
                     node (.getDOMNode compn)]
                 (dommy/listen! node :click #(dommy/add-class! node :top))
                 (.draggable (-> node js/$)
                             (clj->js {:handle "h2"}))))
   })

(defn on-question-edit [eid]
  (fn [e]
    (let [value (.. e -target -value)]
      (d/transact! conn [[:db/add eid :question/answer value]
                         [:db/add eid :question/mark-complete (not (empty? value))]
                         ;[:db.fn/retractAttribute eid :question/mark-complete]
                         ])
      true)))

(rum/defc question-input < rum/static
  [text eid]
  [:input {:type "text"
           :value text
           :on-change (on-question-edit eid)}])

(defn on-option-edit [qid eid]
  (fn [e]
    (let [value (.. e -target -checked)
          op (if value :db/add :db/retract)]
      (d/transact! conn [[op qid :question/options-selected eid]
                         ])
      true)))

(rum/defc question-option < rum/static
  [qid eid]
  (let [option (d/entity @conn eid)
        title (:option/title option)
        id (:option/id option)
        el-id (str "option" qid ":" id)]
    [:span
     [:input {:type "checkbox"
              :on-change (on-option-edit qid eid)
              :id el-id}
      ]
     [:label {:for el-id} title]]
    ))

(rum/defc question < question-mixin
  [id]
  (let [question (d/entity @conn id)
        complete? (some #{id} (questions-done))
        eid (:db/id question)
        answer (:question/answer question)]

    [:li (if complete? {:class "question complete"}
           {:class "question"})
     [:input (merge {:type "checkbox"
                     :id eid
                     :tabIndex "-1"
                     :on-change (fn [e]
                                  (let [c? (.. e -target -checked)]
                                    (d/transact! conn [[:db/add eid :question/mark-complete c?]])))
                     }
                    (if complete?
                      {:checked "checked"}
                      {}))
      ]
     [:label {:for eid} (:question/title question)]
     (when-not (:question/hide_input question)
       (question-input answer eid))
     [:div.options
      (for [eid (:question/options question)]
        (question-option id (:db/id eid)))]

     ]))

(defn section-click [e]
  (let [node (.. e -currentTarget)
        val (-> (dommy/attr node :id)
                (str/split "-")
                second
                int)]
    (when (not= @active-section val)
      (reset! active-section val)
      ;; FIXME
      (rerender @conn))
    true))


(def section-mixin
  {:did-mount
   (fn [state]
     (let [compn (:rum/react-component state)
           node (.getDOMNode compn)]
       ;(dommy/listen! node :click section-click)

       state))
   :wrap-render
   (fn [rfn] (fn [& args]
               ;(js* "debugger;")
               (let [el (sel1 [:#main_content :ul.sections
                               :li :div.active :li.question :input:focus])
                     result (apply rfn args)]
                 (when el (.focus el))
                 result)))
   :will-unmount
   (fn [state]
     (let [compn (:rum/react-component state)
           node (.getDOMNode compn)]
       (dommy/unlisten! node :click section-click)
       state))})


(rum/defc section < section-mixin section-data
  [eid]
  (let [entity (d/entity @conn eid)
        active? (= eid @active-section)
        completed
        (->> (d/q '[:find (count ?q)
                    :in $ % ?nempty ?sid
                    :where
                    [?sid :section/questions ?q]
                    (complete? ?nempty ?q)
                    ] @conn
                  complete?-rules
                  #(not (empty? %))
                  eid)
             first
             (reduce +))
        total (count (:section/questions entity))
        ]
    [:li {:id (str "section-" eid)}
     [:div.bshadow
      (if active? {:class "active"})
      [:h2 (:section/title entity) [:span.right
                                    (str completed "/" total)]]
      [:ul.questions
       (for [eid (:section/questions entity)]
         (rum/with-props question (:db/id eid) :rum/key (:db/id eid)))]]]))


;; (d/q '[:find ?sid ?title
;;        :where [?sid :section/title ?title]] @conn)

(defn asis [items]
  items)

(defn results []
  (for [id (questions-done)]
    (let [d (into {} (d/entity @conn id))
          options (map #(into {} (d/entity @conn (:db/id %)))
                       (:question/options-selected d))]
      (-> d
       (assoc :question/options options)
       (dissoc :question/options-selected)))))


(defn group-by-one
  [f coll]
  (persistent!
   (reduce
    (fn [ret x]
      (let [k (f x)]
        (assoc! ret k x)))
    (transient {}) coll)))

;;(d/q '[:find ?id :where [?id :question/id]] @conn)

(defn generate-report [e]
  (let [data (->> (results)
                  (group-by-one :question/id)
                  (clj->js))]
    (POST (str "/generate/" @client)
          {:params
           {"answers" data
            "formid" (int @form)}
           :handler #(let [rp (get % "report_str")]
                       (log rp)
                       (dommy/set-value! (sel1 :#result) rp))
           :format :json
           :response-format :json
           :error-handler (fn [{:keys [status parse-error]}]
                            (dommy/set-html!
                             (sel1 :#error_main)
                             (:original-text parse-error)))
           })))



(rum/defc editpage < rum/static
  [db]
  (let [completed (->> (d/q '[:find (count ?q)
                              :where
                              [?q :question/mark-complete true]
                              ] @conn)
                       first
                       (reduce +))
        total (->> (d/q '[:find (count ?q)
                          :where [?q :question/title]] @conn)
                   first (reduce +))]
    [:div#editpage
     [:ul.sections#sections
      (for [[order eid] (sort #(compare (first %1) (first %2))
                              (d/q '[:find ?o ?e
                                     :where
                                     [?e :section/title]
                                     [?e :section/order ?o]] db))]
        (section eid))]
     [:br]
     [:input {:type "button"
              :on-click generate-report
              :value "Generate report"}]
     [:div#totalscore
      (str completed "/" total)]
     ]))







(def el-root (.getElementById js/document "main_content"))

(rum/mount (editpage @conn) el-root)

(defn rerender [db]
  (rum/mount (editpage db)
             el-root))

;(rerender @conn)
