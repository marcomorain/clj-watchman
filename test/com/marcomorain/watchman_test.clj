(ns com.marcomorain.watchman-test
  (:require [clojure.test :refer :all]
            [clojure.java.shell :as sh]
            [com.marcomorain.watchman :as w]))

(deftest can-get-sockname
  (let [socket-type (->> (w/get-sockname)
                         (sh/sh "file")
                         :out)]
    (is (.endsWith socket-type ": socket\n"))))

