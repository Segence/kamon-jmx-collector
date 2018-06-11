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
    libraryDependencies += "io.kamon" %% "kamon-jmx-collector" % "0.1.5"
    ```
    - Gradle example:
    ```$groovy
    compile 'io.kamon:kamon-jmx-collector_2.12:0.1.5'
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
              { "name": "AvailableProcessors", "type": "histogram", "keys": [ ] }
            ]
          },
          {
            "metric-name": "os-memory",
            "jmxQuery": "java.lang:type=Memory",
            "attributes": [
              { "name": "HeapMemoryUsage",                "type": "histogram", "keys": [ "committed", "max" ] },
              { "name": "ObjectPendingFinalizationCount", "type": "histogram", "keys": [ ]  }
            ]
          },
          {
            "metric-name": "kafka-consumer",
            "jmxQuery": "kafka.consumer:type=consumer-metrics,client-id=*",
            "attributes": [
              { "name": "connection-count", "type": "histogram", "keys": [ ]  }
            ]
          },
          {
            "metric-name": "kafka-producer-metrics",
            "jmxQuery": "kafka.producer:type=producer-metrics,client-id=*",
            "attributes": [
              { "name": "network-io-rate",    "type": "punctual-gauge", "keys": [ ]  },
              { "name": "outgoing-byte-rate", "type": "punctual-gauge", "keys": [ ]  }
            ]
          },
          {
            "metric-name": "kafka-producer-node-metrics",
            "jmxQuery": "kafka.producer:type=producer-node-metrics,client-id=*,node-id=*",
            "attributes": [
              { "name": "incoming-byte-rate", "type": "punctual-gauge", "keys": [ ]  },
              { "name": "outgoing-byte-rate", "type": "punctual-gauge", "keys": [ ]  }
            ]
          }
        ],
        initial-delay = 1 second,
        value-check-interval = 2 seconds
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

### Configuration details

The following table explains the different configuration parameters and how to set them:

| **Configuration parameter**                        | **Description**                                                                                                                                                                | **Allowed values**                                                                     | **Example value**        |
|:---------------------------------------------------|:-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|:---------------------------------------------------------------------------------------|:-------------------------|
| `kamon.jmx-collector.initial-delay`                | The initial delay before starting to collect metrics, accepted format described [here](https://github.com/lightbend/config/blob/master/HOCON.md#duration-format)               | *(Any duration)*                                                                       | `5 seconds`              |
| `kamon.jmx-collector.value-check-interval`         | The interval to periodically check metric values, accepted format described [here](https://github.com/lightbend/config/blob/master/HOCON.md#duration-format)                   | *(Any duration)*                                                                       | `1 minute`               |
| `kamon.jmx-collector.mbeans[*].metric-name`        | The name of the Kamon metric instrument that is instantiated                                                                                                                   | *(Any)*                                                                                | `memory-consumption`     |
| `kamon.jmx-collector.mbeans[*].jmxQuery`           | The query for the JMX object name                                                                                                                                              | *(Any)*                                                                                | `java.lang:type=Memory`  |
| `kamon.jmx-collector.mbeans[*].attributes[*].name` | The name of one of the attributes under the JMX object                                                                                                                         | *(Any)*                                                                                | `HeapMemoryUsage`        |
| `kamon.jmx-collector.mbeans[*].attributes[*].type` | The type of the Kamon [metric intrument](http://kamon.io/documentation/1.x/core/advanced/metric-instruments/)                                                                  | `counter`, `histogram`, `incrementing-gauge`, `decrementing-gauge`, `punctual-gauge`   | `histogram`              |
| `kamon.jmx-collector.mbeans[*].attributes[*].keys` | The attribute keys if the attribute type is [`CompositeData`](https://docs.oracle.com/javase/8/docs/api/javax/management/openmbean/CompositeData.html), empty array otherwise. | *(Any array expression)*                                                               | `[ "committed", "max" ]` |

As JMX metrics are generated in runtime it is advised to set the `kamon.jmx-collector.initial-delay` parameter to a sensible value, depending on how long it takes for the application to initialise JMX metrics.
Failing to define `kamon.jmx-collector.mbeans[*].attributes[*].keys` for a [`CompositeData`](https://docs.oracle.com/javase/8/docs/api/javax/management/openmbean/CompositeData.html) attribute will make it impossible to retrieve metrics.


Kamon metric names are generated in the following format: `jmx-${metric-name from configuration}-${attribute name}`.
Tags are added to the metrics as long as there are wildcard queries in the `jmxQuery` attribute.

### Finding the available JMX queries and attributes

Either a command line tool like [jmxterm](https://github.com/jiaqi/jmxterm) can be used or a graphical tool like either [JConsole](https://docs.oracle.com/javase/8/docs/technotes/guides/management/jconsole.html) or [VisualVM](https://visualvm.github.io/).
VisualVM needs the `MBeans Browser` plugin installed to see the available JMX Mbeans like on the image below:

![Exploring available MBeans in VisualVM][exploring-mbeans]

It is visible that there are several attributes available under the `java.lang:type=Memory` object name.
`ObjectPendingFinalizationCount` is of type `int` so attribute keys do not needed to be defined in the configuration.

However, `HeapMemoryUsage` is of type `javax.management.openmbean.CompositeData` that can not be converted to a numerical data type directly suitable for Kamon metrics; its attribute keys have to be defined.

The field `openType` contains the following value: `javax.management.openmbean.CompositeType(name=java.lang.management.MemoryUsage,items=((itemName=committed,itemType=javax.management.openmbean.SimpleType(name=java.lang.Long)),(itemName=init,itemType=javax.management.openmbean.SimpleType(name=java.lang.Long)),(itemName=max,itemType=javax.management.openmbean.SimpleType(name=java.lang.Long)),(itemName=used,itemType=javax.management.openmbean.SimpleType(name=java.lang.Long))))`

This shows us the available attribute keys: `committed`, `init`, `max` and `used`. Their data type is `java.lang.Long` that is safe to be used in Kamon metric instruments.

Some of these attribute keys have to be defined in the configuration in order to pass them to Kamon.
They have to be added under the parameter `kamon.jmx-collector.mbeans[*].attributes[*].keys` as a JSON array like `["committed", "init", "max", "used]`.

[exploring-mbeans]: https://github.com/Segence/kamon-jmx-collector/blob/master/exploring-mbeans.png "Exploring available MBeans in VisualVM"
