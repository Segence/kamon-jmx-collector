package kamon.jmx.collector

import javax.management.ObjectName
import kamon.jmx.collector.MetricCollector.{collectMetrics, extractAttributeValue, generateMetricDefinitions, generateMetricName, generateMetrics, getMetricName}
import kamon.jmx.collector.SupportedKamonMetricTypes.{Counter, Histogram}
import org.scalatest.Matchers._
import org.scalatest.WordSpec

import scala.util.Success

class MetricCollectorSpec extends WordSpec {
  "A metric collector" when {
    "getting a metric name" should {
      "return the metric name that exactly matches an object name" in new MetricNameFixture {
        getMetricName(TestObjectName, ObjectNamesWithoutWildcard) shouldBe Some(TestMetricName)
      }
      "return the metric name that matches an object name having wildcard in the end of the query" in new MetricNameFixture {
        getMetricName(TestObjectName, ObjectNamesWithWildcardInTheEndOfQuery) shouldBe Some("kafka-consumer-metric-consumer-1")
      }
      "return the metric name that matches an object name having wildcard in between the query" in new MetricNameFixture {
        getMetricName(TestObjectName, ObjectNamesWithWildcardInBetweenTheQuery) shouldBe Some("kafka-consumer-metric-consumer-1-consumer-metrics")
      }
    }
    "collecting JMX metrics" should {
      "retrieve all available JMX metrics and provide the results with metric names" in new JmxMetricConfigurationFixture {
        val (results, errors) = collectMetrics(configWithValidMBeansAndAttributes)

        results.length shouldBe 3

        results.map(_._1).toSet shouldBe Set("os-mbean", "os-mbean", "memory-mbean")
        results.map(_._2).toSet shouldBe Set("AvailableProcessors", "Arch", "HeapMemoryUsage")

        errors shouldBe empty
      }
      "report any errors in JMX queries" in new JmxMetricConfigurationFixture {
        val (results, errors) = collectMetrics(configWithInvalidJmxQuery)

        results shouldBe empty
        errors.length shouldBe 1
      }
    }
    "generate a metric name from a metric and attribute name pair" should {
      "return a valid metric name without attribute key name" in {
        generateMetricName("AAA", "BBB") shouldBe "jmx-AAA-BBB"
      }
      "return a valid metric name with an attribute key name" in {
        generateMetricName("AAA", "BBB", Some("CCC")) shouldBe "jmx-AAA-BBB-CCC"
      }
    }
    "getting an attribute value" should {
      "return a valid Java Long value" in {
        extractAttributeValue(long2Long(10L)) shouldBe Success(10L)
      }
      "return a valid Scala Long value" in {
        extractAttributeValue(10L) shouldBe Success(10L)
      }
      "return a valid BigDecimal value" in {
        extractAttributeValue(BigDecimal(10L)) shouldBe Success(10L)
      }
      "return an error on a non-numeric value" in {
        extractAttributeValue("10").isFailure shouldBe true
      }
    }
    "generating metrics" should {
      "generate all valid metrics" in new JmxMetricConfigurationFixture {
        val numberOfCPUs = Runtime.getRuntime.availableProcessors.toLong

        val (results, errors) = generateMetrics(configWithValidMBeansAndAttributes)

        results.size shouldBe 3

        results.find(_._1 == "jmx-os-mbean-AvailableProcessors") shouldBe Some(("jmx-os-mbean-AvailableProcessors", numberOfCPUs, Counter))
        results.find(_._1 == "jmx-memory-mbean-HeapMemoryUsage-committed").nonEmpty shouldBe true
        results.find(_._1 == "jmx-memory-mbean-HeapMemoryUsage-max").nonEmpty shouldBe true

        errors.length shouldBe 1
      }
    }
    "generating metric definitions" should {
      "generate all valid metric definitions" in new JmxMetricConfigurationFixture {
        val results = generateMetricDefinitions(configWithValidMBeansAndAttributes)

        results shouldBe Map(
          "jmx-os-mbean-AvailableProcessors" -> Counter,
          "jmx-os-mbean-Arch" -> Histogram,
          "jmx-memory-mbean-HeapMemoryUsage-committed" -> Counter,
          "jmx-memory-mbean-HeapMemoryUsage-max" -> Counter,
          "jmx-memory-mbean-gc" -> Histogram
        )
      }
    }
  }

  trait MetricNameFixture {
    val TestObjectName = new ObjectName("kafka.consumer:type=consumer-metrics,client-id=consumer-1")
    val TestObjectNameWithWildcardInTheEndOfQuery = new ObjectName("kafka.consumer:type=consumer-metrics,client-id=*")
    val TestObjectNameWithWildcardInBetweenTheQuery = new ObjectName("kafka.consumer:type=*,client-id=*")
    val TestMetricName = "kafka-consumer-metric"

    val OtherTestObjectName = new ObjectName("other-some-object-name", "key", "value")
    val OtherTestMetricName = "other-some-metric-name"

    val ObjectNamesWithoutWildcard = Map(
      TestObjectName -> TestMetricName,
      OtherTestObjectName -> OtherTestMetricName
    )

    val ObjectNamesWithWildcardInTheEndOfQuery = Map(
      TestObjectNameWithWildcardInTheEndOfQuery -> TestMetricName,
      OtherTestObjectName -> OtherTestMetricName
    )

    val ObjectNamesWithWildcardInBetweenTheQuery = Map(
      TestObjectNameWithWildcardInBetweenTheQuery -> TestMetricName,
      OtherTestObjectName -> OtherTestMetricName
    )
  }

  trait JmxMetricConfigurationFixture {

    val configWithInvalidJmxQuery =
      JmxMetricConfiguration("os-mbean", "invalid-query",
        JmxMetricAttribute("AvailableProcessors", Counter) :: JmxMetricAttribute("Arch", Histogram) :: Nil
      ) :: Nil

    val configWithValidMBeansAndAttributes =
      JmxMetricConfiguration("os-mbean", "java.lang:type=OperatingSystem",
                             JmxMetricAttribute("AvailableProcessors", Counter) ::
                             JmxMetricAttribute("Arch", Histogram) ::
                             Nil
      ) ::
      JmxMetricConfiguration("memory-mbean", "java.lang:type=Memory",
                             JmxMetricAttribute("HeapMemoryUsage", Counter, "committed" :: "max" :: Nil) ::
                             JmxMetricAttribute("gc", Histogram) ::
                             Nil
      ) :: Nil
  }
}
