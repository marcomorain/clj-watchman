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

(defn write-command [writer command]
  (let [json (str (generate-string command) \newline)]
    (doto writer
      (.print json)
      (.flush))))

(defn execute-command [watchman command]
  ;; Write command
  (write-command (:writer watchman) command)
  ;; Read first result
  (-> watchman
     :reader
     line-seq
     first
     (parse-string true)))

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

;; https://github.com/jnr/jnr-unixsocket/blob/master/src/test/java/jnr/unixsocket/example/UnixClient.java
(defn connect
  ([]
   (connect (get-sockname)))

  ([sockname]
   (let [channel (connect-to-channel sockname)
         reader (-> channel
                    Channels/newInputStream
                    InputStreamReader.
                    reader)
         writer (-> channel
                    Channels/newOutputStream
                    PrintWriter.)]
     (infof "Connected to %s" sockname)
     {:reader reader
      :writer writer })))

