(ns com.marcomorain.watchman
  (:refer-clojure :exclude [find])
  (require [clojure.java.shell :as sh]
           [cheshire.core :refer :all]
           [clojure.tools.logging :refer (infof debugf)]
           [clojure.pprint :refer (pprint)] )
  (use [clojure.java.io :as io])
  (import [jnr.unixsocket UnixSocketAddress UnixSocketChannel]
          [java.io PrintWriter InputStreamReader BufferedReader]
          [java.util.concurrent TimeUnit LinkedBlockingQueue]
          [java.nio.channels Channels]
          [java.nio.charset Charset]
          [java.nio CharBuffer ByteBuffer]))

(defn str->byte-buffer  [s]
  (ByteBuffer/wrap (.getBytes s (Charset/forName "ISO-8859-1"))))

(defn write-command [writer command]
  (let [json (str (generate-string command) \newline)
        byte-buffer (str->byte-buffer json)]
    (.write writer byte-buffer)))

(defn execute-command [watchman command]
  (write-command (:channel watchman) command)
  (.poll (:queue watchman) 5 TimeUnit/SECONDS))

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

(defn result-reader [reader queue]
  ;; The Java thread constructor expects a function with
  ;; zero arguments so we close over reader and queue
  (fn []
    (doseq [line (line-seq reader)
            :let [message (parse-string line true)]]
      (infof "msg: %s" message)
      (cond
        ;; Dispatch based on message type
        (:log message) (infof "Log: %s" message)
        (:subscription message) (infof "Subscription: %s" message)
        :else (.put queue message)))))

(defn connect
  ([]
   (connect (get-sockname)))

  ([sockname]
   (let [channel (connect-to-channel sockname)
         reader (-> channel
                    Channels/newInputStream
                    InputStreamReader.
                    reader)
         queue (LinkedBlockingQueue.)
         thread (doto (Thread. (result-reader reader queue))
                  (.setDaemon true)
                  (.start))]
     (infof "Connected to %s" sockname)
     {:reader reader
      :queue queue
      :channel channel})))

(defmacro defcmd [name params]
  "Create a watchman command with the given signature"
  `(defn ~name ~params
     (execute-command ~(first params) [~(str name) ~@(rest params)])))

(defcmd clock [watchman path])
(defcmd get-config [watchman path])
(defcmd log [watchman level log])
(defcmd log-level [watchman level])
(defcmd query [watchman path query])
(defcmd subscribe [watchman path name sub])
(defcmd trigger [watchman path triggerobj])
(defcmd trigger-del [watchman path triggername])
(defcmd trigger-list [watchman path triggername])
(defcmd unsubscribe [watchman path name])
(defcmd version [watchman])
(defcmd watch [watchman path])
(defcmd watch-del [watchman path])
(defcmd watch-del-all [watchman])
(defcmd watch-list [watchman])
(defcmd watch-project [watchman path])

(defn since [watchman path clockspec & patterns]
  (execute-command watchman (list* "since" path clockspec patterns)))

(defn find [watchman path & patterns]
  (execute-command watchman (list* "find" path patterns)))
