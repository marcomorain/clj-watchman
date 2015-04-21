(ns com.marcomorain.watchman
  (:refer-clojure :exclude [find])
  (require [clojure.java.shell :as sh]
           [cheshire.core :refer :all]
           [clojure.tools.logging :refer (infof)])
  (use [clojure.java.io :as io])
  (import [jnr.unixsocket UnixSocketAddress UnixSocketChannel]
          [java.io PrintWriter InputStreamReader BufferedReader]
          [java.nio.channels Channels]
          [java.nio.charset Charset]
          [java.nio CharBuffer ByteBuffer]))

;; todo type annotation
(defn read-response [reader]
  (parse-string (.readLine reader) true))


(defn message-type [message]
  (cond
    (:error message) :error))

(defmulti on-message message-type)

(defmethod on-message :error
  [message]
  (infof "Error message: %s" message))

(defmethod on-message :default
  [message]
  (infof "Normal message %s" message))

;; todo type annotation
;; todo: don't make a new byte buffer on each command
(defn write-command [writer command]
  (let [json (str (generate-string command) \newline)
        json-bytes (.getBytes json (Charset/forName "ISO-8859-1"))
        byte-buffer (ByteBuffer/wrap json-bytes)
        _ (infof "Writing command %s" json)
        n (.write writer byte-buffer)]
    (infof "wrote %d bytes" n)))

(defn execute-command [watchman command]
  (write-command (:channel watchman) command))

(defn get-sockname []
  (-> (sh/sh "watchman" "get-sockname")
      :out
      (parse-string true)
      :sockname))

(defn listener [reader]
  (infof "Listener started")
  (fn []
    (on-message (read-response reader))
    (recur)))

(defn connect
  ([]
   (connect (get-sockname)))
  ([sockname]
   (let [path (io/file sockname)
         address (UnixSocketAddress. path)
         channel (UnixSocketChannel/open address)
         input (InputStreamReader. (Channels/newInputStream channel))
         reader (BufferedReader. input)
         thread (doto
                  (Thread. (listener reader))
                  (.setDaemon true)
                  (.start))]
     (infof "Connected to %s" sockname)
     {:thread thread
      :reader reader
      :channel channel})))

;; Commands - make these from a macro
(defn get-config [watchman path]
  (execute-command watchman ["get-config" path]))

(defn clock [watchman path]
  (execute-command watchman ["clock" path]))

(defn find [watchman path & patterns]
  (execute-command watchman (list* "find" path patterns)))

(defn log [watchman level log]
  (execute-command watchman ["log" level log]))

(defn log-level [watchman level]
  (execute-command watchman ["log-level" level]))

(defn subscribe [watchman path name sub]
  (execute-command watchman ["subscribe" path name sub]))

(defn unsubscribe [watchman path name]
  (execute-command watchman ["unsubscribe" path name]))

(defn version [watchman]
  (execute-command watchman ["version"]))

(defn watch [watchman path]
  (execute-command watchman ["watch" path]))

(defn watch-list [watchman]
  (execute-command watchman ["watch-list"]))
