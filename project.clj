(defproject kulu-backend "0.1.0-SNAPSHOT"
  :description "Kulu backend service"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.6.0"]

                 [metosin/compojure-api "0.15.0"]
                 [metosin/ring-swagger-ui "2.0.17"]
                 [ring/ring-jetty-adapter "1.3.0"]
                 [ring/ring-devel "1.3.0"]
                 [com.duelinmarkers/ring-request-logging "0.2.0"]
                 [ring-middleware-format "0.3.2"]
                 [metosin/ring-http-response "0.4.1"]

                 [org.clojure/tools.logging "0.3.0"]
                 [org.slf4j/slf4j-log4j12 "1.6.6"]

                 [jarohen/nomad "0.7.0"]
                 [clj-time "0.8.0"]
                 [org.clojure/data.json "0.2.5"]
                 [org.clojars.runa/conjure "2.1.3"]
                 [clj-http "0.9.1"]

                 [org.clojure/java.jdbc "0.3.4"]
                 [com.jolbox/bonecp "0.8.0.RELEASE"]
                 [org.postgresql/postgresql "42.3.1"]
                 [honeysql "0.4.3"]
                 [clj-sql-up "0.3.3"]
                 [stencil "0.3.5"]
                 [com.taoensso/carmine "2.9.2"]
                 [com.cemerick/url "0.1.1"]

                 [com.amazonaws/aws-java-sdk "1.7.5" :exclusions [joda-time]]
                 [clj-aws-s3 "0.3.9" :exclusions [joda-time]]
                 [com.cemerick/bandalore "0.0.5"]

                 [clojurewerkz/elastisch "2.1.0-beta5"]
                 [com.cemerick/friend "0.2.0"]
                 [org.clojure/core.incubator "0.1.3"]
                 [org.clojure/tools.nrepl "0.2.10"]
                 [dk.ative/docjure "1.10.0"]

                 [nilenso/mailgun "0.2.2"]]

  :plugins [[lein-ring "0.8.11"]
            [clj-sql-up "0.3.7"]]
  :jvm-opts ["-Dnomad.env=dev"]
  :profiles {:uberjar {:resource-paths ["swagger-ui"]}
             :production {:jvm-opts ["-Dnomad.env=production"]}}
  :clj-sql-up {:database ~(str "jdbc:" (System/getenv "DATABASE_URL"))
               :deps [[org.postgresql/postgresql "9.3-1100-jdbc4"]]}
  :resource-paths ["resources"]
  :aliases {"setup" ["run" "-m" "kulu-backend.tasks" "setup"]
            "migrate-indexes" ["run" "-m" "kulu-backend.tasks" "migrate-indexes"]})
