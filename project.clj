(defproject keyssrv "0.1.0-SNAPSHOT"

  :description "FIXME: write description"
  :url "http://example.com/FIXME"

  :dependencies [[ch.qos.logback/logback-classic "1.2.3"]
                 [clj-time "0.15.1"]
                 [compojure "1.6.1"]
                 [clj-http "3.10.0"]

                 [metosin/compojure-api "2.0.0-alpha26"]


                 [conman "0.8.3"]
                 [cprop "0.1.13"]
                 [funcool/struct "1.3.0"]
                 [luminus-migrations "0.5.0"]

                 [luminus-nrepl "0.1.6"]
                 [luminus/ring-ttl-session "0.3.3"]
                 [markdown-clj "1.10.0"]
                 [metosin/muuntaja "0.5.0"]
                 [metosin/ring-http-response "0.9.1"]

                 [listora/again "1.0.0" :scope "test"]
                 [mount "0.1.16"]
                 [camel-snake-kebab "0.4.0"]
                 [org.clojure/clojure "1.10.0"]
                 [org.clojure/tools.cli "0.4.2"]
                 [org.clojure/tools.logging "0.5.0-alpha.1"]
                 [org.postgresql/postgresql "42.2.2"]
                 [org.webjars.bower/tether "1.4.4"]
                 [org.webjars/bootstrap "4.3.1"]
                 [org.webjars/popper.js "1.15.0"]

                 [org.webjars/font-awesome "5.8.2"]
                 [org.webjars/jquery "3.4.1"]
                 [org.webjars/webjars-locator "0.36"]
                 [ring-webjars "0.2.0"]
                 [ring/ring-core "1.7.1"]
                 [ring/ring-defaults "0.3.2"]
                 [fun-utils "0.7.0" :exclude [org.clojure/core.async]]
                 [org.clojure/core.async "0.4.490"]
                 [danlentz/clj-uuid "0.1.7"]

                 [redis.clients/jedis "3.0.1"]

                 [buddy/buddy-sign "3.0.0"]
                 [buddy/buddy-hashers "1.3.0"]

                 [http-kit "2.3.0"]

                 [selmer "1.12.12"]

                 [com.taoensso/nippy "2.14.0"]
                 [com.github.luben/zstd-jni "1.4.0-1"]

                 [com.google.guava/guava "25.1-jre"]

                 [javax.mail/javax.mail-api "1.6.2"]
                 [com.draines/postal "2.0.3"]


                 [com.github.gerritjvv/codex "1.2.8"]

                 [one-time "0.5.0"]

                 [ring-cors "0.1.13"]

                 [metosin/jsonista "0.2.2"]

                 [juxt/dirwatch "0.2.5"]

                 ;; i8n http://www.luminusweb.net/docs/i18n.html
                 [com.taoensso/tempura "1.2.1"]
                 ;; cache buster and optimization
                 [optimus "0.20.2"]

                 [com.stripe/stripe-java "10.0.2"]
                 ;;;;;;;;;;;;;;;;;;;;;;; Front end dependencies

                 [clojurewerkz/quartzite "2.1.0"]

                 [org.webjars/clipboard.js "2.0.4"]
                 [org.webjars.npm/tooltip.js "1.3.2"]
                 [org.webjars.bower/simplemde-markdown-editor "1.11.2"]
                 [org.webjars.npm/bootstrap-select "1.13.10"]

                 [org.webjars/datatables "1.10.19"]


                 [com.kosprov.jargon2/jargon2-api "1.1.1"]
                 [com.kosprov.jargon2/jargon2-native-ri-backend "1.1.1"]

                 [org.apache.sshd/sshd-core "2.2.0" :scope "test"]
                 [listora/again "1.0.0" :scope "test"]
                 [sjdbc "0.1.7" :scope "test"]

                 ]

  :min-lein-version "2.0.0"

  :source-paths ["src/clj"]
  :java-source-paths ["src/java"]

  :main keyssrv.core
  :aot [keyssrv.core]

  :test-paths ["test/clj"]
  :resource-paths ["resources"]
  :target-path "target"

  :clean-targets {:protect true}

  :plugins [[lein-libdir "0.1.1"]
            [lein-ancient "0.5.5"]
            [lein-annotations "0.1.0"]
            [lein-cljsbuild "1.1.7"]
            [lein-kibit "0.1.6"]
            [jonase/eastwood "0.3.5"]
            [lein-nvd "0.5.4"]]

  :repl-options {:timeout 120000}

  ;;note on resources, we include them directly into the docker image
  ;; rather than using [lein-resource "16.9.1"] to copy them
  :libdir-path "target/lib"
  :global-vars {
                *warn-on-reflection* true
                *assert*             false}

  :javac-options ["-target" "1.8" "-source" "1.8" "-Xlint:-options"]
  :prep-tasks ["libdir"]

  :jar-name "keyssrv.jar"

  :profiles
  {
   :uberjar       {:omit-source    true
                   :aot            :all
                   :source-paths   ["src/clj" "env/prod/clj"]
                   :resource-paths ["env/prod/resources"]}

   :jar           {:omit-source    false
                   :aot            :all
                   :source-paths   ["src/clj" "env/prod/clj" "target/classes"]
                   :resource-paths ["env/prod/resources"]

                   }


   :dev           [:project/dev :profiles/dev]
   :test          [:project/dev :project/test :profiles/test]

   :project/dev   {:jvm-opts       ["-Dconf=dev-config.edn"]
                   :dependencies   [[expound "0.7.2"]
                                    [pjstadig/humane-test-output "0.9.0"]
                                    [prone "1.6.3"]
                                    [ring/ring-devel "1.7.1"]
                                    [ring/ring-mock "0.4.0"]

                                    ;; https://mvnrepository.com/artifact/com.opentable.components/otj-pg-embedded
                                    ;[lispyclouds/clj-docker-client "0.2.2"]

                                    [mysql/mysql-connector-java "8.0.16"]
                                    ]
                   :plugins        [[com.jakemccrary/lein-test-refresh "0.19.0"]]

                   :source-paths   ["env/dev/clj"]
                   :resource-paths ["env/dev/resources"
                                    "resources"]

                   :prep-tasks     ["javac" "compile"]
                   :repl-options   {:init-ns kuser}
                   :injections     [(require 'pjstadig.humane-test-output)
                                    (pjstadig.humane-test-output/activate!)]
                   }
   :project/test  {:jvm-opts       ["-Dconf=test-config.edn"]
                   :source-paths   ["env/test/clj"]
                   :resource-paths ["env/test/resources"
                                    "resources"]}
   :profiles/dev  {}
   :profiles/test {}})
