(ns ^:figwheel-no-load loja.start
  (:require
   [loja.core :as core]))


(extend-protocol IPrintWithWriter
  js/Symbol
  (-pr-writer [sym writer _]
    (-write writer (str "\"" (.toString sym) "\""))))

(core/init! false)
