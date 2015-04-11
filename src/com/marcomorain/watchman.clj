(ns com.marcomorain.watchman
  (require
    [clojure.java.shell :as sh]
    [cheshire.core :refer :all]))

(defn- get-sockname []
  (-> (sh/sh "watchman" "get-sockname")
      :out
      (parse-string true)
      :sockname))

(defn foo
  "I don't do a whole lot."
  [x]
  (println x "Hello, World!"))
