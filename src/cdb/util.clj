(ns cdb.util
  (:require [cljs.closure :as cljsc]
            [cljs.env :as cljs-env :refer [with-compiler-env]]))

(defmacro log
  "Display messages to the console."
  [& args]
  `(.log js/console ~@args))


(defmacro profile [k & body]
  `(let [k# ~k]
     (.time js/console k#)
     (let [res# (do ~@body)]
       (.timeEnd js/console k#)
       res#)))


(defmacro dbgr
  []
  `(js* "debugger;"))

