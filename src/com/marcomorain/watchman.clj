(ns com.marcomorain.watchman
  (require [clojure.java.shell :as sh]
           [cheshire.core :refer :all]
           [clojure.tools.logging :refer (infof)]
           )
  (use [clojure.java.io :as io])
  (import [jnr.unixsocket UnixSocketAddress UnixSocketChannel]
          [java.io PrintWriter InputStreamReader BufferedReader]
          [java.nio.channels Channels]
          [java.nio CharBuffer]))

(defn execute-command [watchman command]
  (let [json (str (generate-string command) \newline)]
    (doto (:writer watchman)
      (.print json)
      (.flush)))
  (let [response (-> watchman
                     :reader
                     .readLine
                     (parse-string true))]
    (when (contains? response :error)
      (throw (IllegalArgumentException. (:error response))))
    response))

(defn- get-sockname []
  (-> (sh/sh "watchman" "get-sockname")
      :out
      (parse-string true)
      :sockname))

(defn connect
  ([]
   (connect (get-sockname)))
  ([sockname]
   (let [path (io/file sockname)
         address (UnixSocketAddress. path)
         channel (UnixSocketChannel/open address)
         writer (PrintWriter. (Channels/newOutputStream channel))
         input (InputStreamReader. (Channels/newInputStream channel))
         reader (BufferedReader. input)]
     (infof "Connected to %s" sockname)
     {:reader reader
      :writer writer})))

;; Commands - make these from a macro
(defn get-config [watchman path]
  (execute-command watchman ["get-config" path]))

(defn clock [watchman path]
  (execute-command watchman ["clock" path]))

(defn log-level [watchman level]
  (execute-command watchman ["log-level" level]))

(defn version [watchman]
  (execute-command watchman ["version"]))
