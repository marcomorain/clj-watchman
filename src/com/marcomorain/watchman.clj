(ns com.marcomorain.watchman
  (:refer-clojure :exclude [find])
  (require [clojure.java.shell :as sh]
           [cheshire.core :refer :all]
           [clojure.tools.logging :refer (infof debugf)])
  (use [clojure.java.io :as io])
  (import [jnr.unixsocket UnixSocketAddress UnixSocketChannel]
          [java.io PrintWriter InputStreamReader BufferedReader]
          [java.util.concurrent TimeUnit LinkedBlockingQueue]
          [java.nio.channels Channels]
          [java.nio.charset Charset]
          [java.nio CharBuffer ByteBuffer]))

(defn on-message
  [queue message f]
  (cond
    (:log message) (infof "Log message: %s" message)
    (:subscription message) (f message)
    :else (.put queue message)))

(defn str->byte-buffer [s]
  (ByteBuffer/wrap (.getBytes s (Charset/forName "ISO-8859-1"))))

;; todo type annotation
;; todo: don't make a new byte buffer on each command
(defn write-command [writer command]
  (let [json (str (generate-string command) \newline)
        byte-buffer (str->byte-buffer json)]
    (.write writer byte-buffer)))

(defn execute-command [watchman command]
  (write-command (:channel watchman) command)
  (.poll (:queue watchman) 5 TimeUnit/SECONDS))

(defn- message-reader [queue reader f]
  (fn []
    (on-message queue
                (parse-string (.readLine reader) true)
                f)
    (recur)))

(defn- connect-to-channel [sockname]
  (-> sockname
      io/file
      UnixSocketAddress.
      UnixSocketChannel/open))

;; Special command - needed to connect
(defn get-sockname []
  (-> (sh/sh "watchman" "get-sockname")
      :out
      (parse-string true)
      :sockname))

(defn connect
  ([f]
   (connect (get-sockname) f))
  ([sockname f]
   (let [channel (connect-to-channel sockname)
         reader (-> channel
                    Channels/newInputStream
                    InputStreamReader.
                    reader)
         queue (LinkedBlockingQueue.)
         ;; TODO: function to close thread
         thread (doto
                  (Thread. (message-reader queue reader f))
                  (.setDaemon true)
                  (.start))]
     (infof "Connected to %s" sockname)
     {:channel channel
      :queue queue
      :thread thread})))

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
