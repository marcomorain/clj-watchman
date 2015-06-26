(ns com.marcomorain.watchman-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [clojure.java.shell :as sh]
            [com.marcomorain.watchman :as w]))

(deftest can-get-sockname
  (let [socket-type (->> (w/get-sockname)
                         (sh/sh "file")
                         :out)]
    (is (.endsWith socket-type ": socket\n"))))

(deftest can-send-command
  (let [s (w/connect identity)]
    (is (= {:version "3.1.0"}
           (w/execute-command s ["version"])))))

(deftest can-call-functions
  (let [pwd (-> "pwd"
                sh/sh
                :out
                str/trim)
        s (w/connect identity)]
    (is (= pwd (:watch (w/watch s pwd))))
    (is (re-matches #"c:[0-9:]*" (:clock (w/clock s pwd))))))

(comment
  ;; Check output:
  (require '[com.marcomorain.watchman :as w] :reload)
  (def s (w/connect identity))
  (w/execute-command s ["log-level" "debug"])
  (w/execute-command s ["log-level" "error"])

  )
