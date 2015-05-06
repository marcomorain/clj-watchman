(ns com.marcomorain.watchman-test
  (:require [clojure.test :refer :all]
            [clojure.java.shell :as sh]
            [com.marcomorain.watchman :as w]))

(deftest can-get-sockname
  (let [socket-type (->> (w/get-sockname)
                         (sh/sh "file")
                         :out)]
    (is (.endsWith socket-type ": socket\n"))))

(deftest can-send-command
  (let [s (w/connect)]
    (is (= {:version "3.1.0"}
           (w/execute-command s ["version"])))))



(comment
  ;; Check output:
  (require '[com.marcomorain.watchman :as w] :reload)
  (def s (w/connect))
  (w/execute-command s ["log-level" "debug"])
  (w/execute-command s ["log-level" "error"])

  )
