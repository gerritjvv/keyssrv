(ns
  ^{:doc "
  Run commands, async print to stdout and collect the output as a vector
  usage:
     (def out-lines (sh \"ls\" \"-la\"))

     ;; out-lines are the output lines, you should also see lines printed as they appear
     ;; to stdout

  "}
  keyssrv.test.sh
  (:require [camel-snake-kebab.core :as snake])
  (:import (java.util.concurrent CountDownLatch ExecutorService TimeUnit TimeoutException Executors)
           (java.util List Map)
           (java.io InputStream IOException)
           (org.apache.commons.lang3 StringUtils)))


(defn daemon-runnable ^ExecutorService [f]
  (let [r (reify Runnable
            (run [_]
              (try
                (f)
                (catch Exception e
                  (prn e)
                  (.printStackTrace e)))))
        exec (Executors/newSingleThreadExecutor)]

    (.submit exec ^Runnable r)
    exec))

(defn read-line'
  "Catch Stream closed and returrn nil
  Requires the bindnig *in*"
  []
  (try
    (read-line)
    (catch IOException e
      (when-not (StringUtils/contains (.getMessage e) "Stream closed")
        (throw e)))))

(defn output-collector
  "Return an atom with a vector of lines
  Read each line from in, prn out the line and conj to the atom's []"
  [^InputStream in]
  (let [output (atom [])
        waiter (CountDownLatch. (int 1))
        service (daemon-runnable (fn []
                                   (with-open [rd (clojure.java.io/reader in)]
                                     (binding [*in* rd]
                                       (try
                                         (loop [line (read-line')]
                                           (when line
                                             (prn line)
                                             (swap! output conj line)
                                             (recur (read-line'))))
                                         (finally
                                           (.countDown waiter)))))))]
    {:lines   output :waiter waiter
     :service service}))

(defn wait-on-cmd [{:keys [^CountDownLatch waiter ^ExecutorService service]}]
  (let [res (.await waiter 1 TimeUnit/MINUTES)]
    (.shutdownNow service)
    (when-not res
      (throw (TimeoutException.)))))

(defn build-command ^ProcessBuilder [env & args]
  (let [builder (doto
                  (ProcessBuilder. ^List (mapv str args))
                  (.redirectErrorStream true))]

    ;;set the env variables to the process environment
    ;; support keywords
    (reduce-kv (fn [^Map m k v]
                 (doto m
                   (.put (name (snake/->SCREAMING_SNAKE_CASE k)) (str v))))
               (.environment builder) env)

    builder))

(defn sh [env & args]
  (let [
        p (apply build-command env args)

        _ (do

            (prn "Invoking " {:command args
                              :env     env}))
        ^Process res (.start ^ProcessBuilder p)

        ctx (output-collector (.getInputStream res))
        lines (:lines ctx)]

    (when-not (.waitFor res 10 TimeUnit/MINUTES)
      (.destroy res)
      (throw (TimeoutException. (str "Timeout waiting for " args))))

    (wait-on-cmd ctx)

    (prn "##### test.sh: cmd return value: " (.exitValue res))
    (when-not (zero? (.exitValue res))
      (prn "Output: " lines)
      (throw (RuntimeException. (str "Error exit-code: " (.exitValue res) " for args " args))))

    @lines))

(defn sh-no-exception [env & args]
  (let [
        p (apply build-command env args)

        _ (do

            (prn "Invoking " {:command args
                              :env     env}))
        ^Process res (.start ^ProcessBuilder p)

        ctx (output-collector (.getInputStream res))
        lines (:lines ctx)]

    (when-not (.waitFor res 10 TimeUnit/MINUTES)
      (.destroy res)
      (throw (TimeoutException. (str "Timeout waiting for " args))))

    (wait-on-cmd ctx)

    (prn "##### test.sh: cmd return value: " (.exitValue res))
    [(.exitValue res) @lines]))


(defn sh-async
  "Used to run commands that run async to the current process/.
   Use the kill-cmd to stop command started with this function.
  return: {:process ^Process :output {:lines (atom []} :waiter CountdownLatch :service ExecutorService}"
  [env & args]
  (let [
        p (apply build-command env args)

        _ (do

            (prn "Invoking " {:command args
                              :env     env}))
        ^Process res (.start ^ProcessBuilder p)


        ctx (output-collector (.getInputStream res))
        ]


    {:process res
     :output  ctx}))

(defn kill-cmd
  "Destroy processes started with sh-async"
  [{:keys [process output]}]
  {:pre [(instance? Process process) (instance? ExecutorService (:service output))]}
  (.shutdownNow ^ExecutorService (:service output))
  (Thread/sleep 200)
  (.destroy ^Process process))