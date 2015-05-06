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

