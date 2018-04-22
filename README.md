Kamon JMX Collector
===================

[![Build Status](https://travis-ci.org/Segence/kamon-jmx-collector.svg?branch=master)](https://travis-ci.org/Segence/kamon-jmx-collector)
[ ![Download](https://api.bintray.com/packages/segence/maven-oss-releases/kamon-jmx-collector/images/download.svg) ](https://bintray.com/segence/maven-oss-releases/kamon-jmx-collector/_latestVersion)

JMX metrics collector for [Kamon](http://kamon.io).
Compatible with the `1.x` branch.
It was inspired by the [Kamon JMX](https://github.com/kamon-io/kamon-jmx) library that is only compatible with the legacy version of Kamon.

Usage
-----

1. Add the Segence OSS Releases Maven repo to your project, [click here](https://bintray.com/segence/maven-oss-releases/jmx-collector) for instructions
2. Import the artifact, [click here](https://bintray.com/segence/maven-oss-releases/jmx-collector) for instructions.
    - SBT example:
    ```$scala
    libraryDependencies += "io.kamon" %% "kamon-jmx-collector" % "0.1.1"
    ```
    - Gradle example:
    ```$groovy
    compile 'io.kamon:kamon-jmx-collector_2.12:0.1.1'
    ```
3. The artifact does not include all dependencies to avoid versioning conflict within your application.
   The required libraries have to be added.
     - SBT example:
     ```$scala
     libraryDependencies += "io.kamon" %% "kamon-core" % "1.1.2"
     libraryDependencies += "com.typesafe" % "config" % "1.3.2"
     libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.5.12"
     ```
     - Gradle example:
     ```$groovy
      compile(
        "io.kamon:kamon-core_2.12:1.1.2",
        "com.typesafe:config:1.3.2",
        "com.typesafe.akka:akka-actor_2.12:2.5.12"
      )
     ```
 4. Add a configuration to your `application.conf` file describing the `MBeans` to query. Example:
     ```$json
     kamon {
       jmx-collector {
         mbeans = [
           {
             "metric-name": "os-mbean",
             "jmxQuery": "java.lang:type=OperatingSystem",
             "attributes": [
               { "name": "AvailableProcessors", "type": "histogram" }
             ]
           },
           {
             "metric-name": "os-memory",
             "jmxQuery": "java.lang:type=Memory",
             "attributes": [
               { "name": "ObjectPendingFinalizationCount", "type": "histogram" }
             ]
           }
         ],
         initial-delay = 1 second,
         value-check-interval-ms = 2 seconds
       }
     }
     ```
 5. Launch the metric collector. Example:
    ```$scala
    import akka.actor.ActorSystem
    import kamon.Kamon
    ...
    implicit val system = ActorSystem()
    KamonJmxMetricCollector()
    ```
