package kamon.jmx.collector

import kamon.jmx.collector.MetricCollector.{collectMetrics, extractAttributeValue, generateMetricName, generateMetrics, generateMetricDefinitions}
import kamon.jmx.collector.SupportedKamonMetricTypes.{Counter, Histogram}
import org.scalatest.Matchers._
import org.scalatest.WordSpec

import scala.util.Success

class MetricCollectorSpec extends WordSpec {
  "A metric collector" when {
    "collecting JMX metrics" should {
      "retrieve all available JMX metrics and provide the results with metric names" in new Fixture {
        val (results, errors) = collectMetrics(configWithValidMBeansAndAttributes)

        results.length shouldBe 3

        results.map(_._1).toSet shouldBe Set("os-mbean", "os-mbean", "memory-mbean")
        results.map(_._2).toSet shouldBe Set("AvailableProcessors", "Arch", "HeapMemoryUsage")

        errors shouldBe empty
      }
      "report any errors in JMX queries" in new Fixture {
        val (results, errors) = collectMetrics(configWithInvalidJmxQuery)

        results shouldBe empty
        errors.length shouldBe 1
      }
    }
    "generate a metric name from a metric and attribute name pair" should {
      "return a valid metric name" in {
        generateMetricName("AAA", "BBB") shouldBe "jmx-AAA-BBB"
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
      "generate all valid metrics" in new Fixture {
        val numberOfCPUs = Runtime.getRuntime().availableProcessors().toLong

        val (results, errors) = generateMetrics(configWithValidMBeansAndAttributes)

        results shouldBe Map("jmx-os-mbean-AvailableProcessors" -> numberOfCPUs)
        errors.length shouldBe 2
      }
    }
    "generating metric definitions" should {
      "generate all valid metric definitions" in new Fixture {
        val results = generateMetricDefinitions(configWithValidMBeansAndAttributes)

        results shouldBe Map(
          "jmx-os-mbean-AvailableProcessors" -> Counter,
          "jmx-os-mbean-Arch" -> Histogram,
          "jmx-memory-mbean-HeapMemoryUsage" -> Counter,
          "jmx-memory-mbean-gc" -> Histogram
        )
      }
    }
  }

  trait Fixture {

    val configWithInvalidJmxQuery =
      JmxMetricConfiguration("os-mbean", "invalid-query",
        JmxMetricAttribute("AvailableProcessors", Counter) :: JmxMetricAttribute("Arch", Histogram) :: Nil
      ) :: Nil

    val configWithValidMBeansAndAttributes =
      JmxMetricConfiguration("os-mbean", "java.lang:type=OperatingSystem",
                             JmxMetricAttribute("AvailableProcessors", Counter) :: JmxMetricAttribute("Arch", Histogram) :: Nil
      ) ::
      JmxMetricConfiguration("memory-mbean", "java.lang:type=Memory",
        JmxMetricAttribute("HeapMemoryUsage", Counter) :: JmxMetricAttribute("gc", Histogram) :: Nil
      ) :: Nil
  }
}
