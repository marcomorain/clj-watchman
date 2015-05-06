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
  (.poll (:queue watchman)))

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
  (fn []
    (doseq [line (line-seq reader)
            :let [message (parse-string line true)]]
      (cond
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


;; Add the commands

(defn clock [watchman path]
  (execute-command watchman ["clock" path]))

(defn find [watchman path & patterns]
  (execute-command watchman (list* "find" path patterns)))

(defn get-config [watchman path]
  (execute-command watchman ["get-config" path]))

(defn log [watchman level log]
  (execute-command watchman ["log" level log]))

(defn log-level [watchman level]
  (execute-command watchman ["log-level" level]))

(defn query [watchman path query]
  (execute-command watchman ["query" path query]))

(defn since [watchman path clockspec & patterns]
  (execute-command watchman (list* "since" path clockspec patterns)))

(defn subscribe [watchman path name sub]
  (execute-command watchman ["subscribe" path name sub]))

(defn trigger [watchman path triggerobj]
  (execute-command watchman ["trigger" path triggerobj]))

(defn trigger-del [watchman path triggername]
  (execute-command watchman ["trigger-del" path triggername]))

(defn trigger-list [watchman path triggername]
  (execute-command watchman ["trigger-list" path]))

(defn unsubscribe [watchman path name]
  (execute-command watchman ["unsubscribe" path name]))

(defn version [watchman]
  (execute-command watchman ["version"]))

(defn watch [watchman path]
  (execute-command watchman ["watch" path]))

(defn watch-del [watchman path]
  (execute-command watchman ["watch-del" path]))

(defn watch-del-all [watchman]
  (execute-command watchman ["watch-del-all"]))

(defn watch-list [watchman]
  (execute-command watchman ["watch-list"]))

(defn watch-project [watchman path]
  (execute-command watchman ["watch-project" path]))
