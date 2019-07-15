package kamon.jmx.collector

import com.segence.commons.jmx.collector.{JmxCollector, MBeanMetricResult}
import javax.management.ObjectName
import kamon.jmx.collector.MetricCollector._
import kamon.jmx.collector.SupportedKamonMetricTypes.{Counter, Histogram}
import org.scalatest.Matchers._
import org.scalatest.WordSpec
import org.scalatest.mockito.MockitoSugar
import java.{util => javautil}

import kamon.tag.TagSet
import org.mockito.Mockito.when
import org.mockito.ArgumentMatchers.any

import scala.collection.JavaConverters._
import scala.util.Success

class MetricCollectorSpec extends WordSpec {
  "A metric collector" when {
    "getting a metric name" should {
      "return the metric name that exactly matches an object name" in new MetricNameFixture {
        getMetricName(KafkaConsumerObjectName, ObjectNamesWithoutWildcard) shouldBe Some(MetricMetadata("kafka-consumer-metric"))
      }
      "return the metric name that matches an object name having wildcard in the end of the query" in new MetricNameFixture {
        getMetricName(KafkaConsumerObjectName, ObjectNamesWithWildcardInTheEndOfQuery) shouldBe Some(MetricMetadata("kafka-consumer-metric", TagSet.from(Map("client-id" -> "consumer-1", "type" -> "consumer-metrics"))))
      }
      "return the metric name that matches an object name having wildcard in between the query" in new MetricNameFixture {
        getMetricName(KafkaConsumerObjectName, ObjectNamesWithWildcardInBetweenTheQuery) shouldBe Some(MetricMetadata("kafka-consumer-metric", TagSet.from(Map("type" -> "consumer-metrics", "client-id" -> "consumer-1"))))
      }
      "return valid metric name when multiple values are wildcards" in new MetricNameFixture {
        getMetricName(KafkaProducerObjectName, ObjectNamesWithWildcardInValues) shouldBe Some(MetricMetadata("kafka-producer2", TagSet.from(Map("type" -> "producer-node-metrics", "node-id" -> "node-1", "client-id" -> "producer-1"))))
      }
      "return the metrics name that matches the object name and all properties when properties overlap" in new SimilarMetricNameFixture {
        getMetricName(KafkaConsumerObjectName, ObjectNamesWithWildcardInValues) shouldBe Some(MetricMetadata("kafka-consumer2", TagSet.from(Map("type" -> "consumer-fetch-manager-metrics", "client-id" -> "client-1"))))
        getMetricName(KafkaConsumerExtendedObjectName, ObjectNamesWithWildcardInValues) shouldBe Some(MetricMetadata("kafka-consumer1", TagSet.from(Map("topic" -> "test-topic", "type" -> "consumer-fetch-manager-metrics", "client-id" -> "client-1"))))
      }
    }
    "collecting JMX metrics" should {
      "retrieve all available JMX metrics and provide the results with metric names" in new JmxMetricConfigurationFixture {
        val (results, errors) = collectMetrics(
          expectedJmxMbeansAndAttributes,
          expectedJmxMbeansAndMetricNames,
          expectedConfigWithObjectNames,
          Nil,
          JmxCollector.queryAsSet
        )

        results.length shouldBe 3

        results.map(_._1).toSet shouldBe Set(MetricMetadata("os-mbean"), MetricMetadata("memory-mbean"))
        results.map(_._2).toSet shouldBe Set("AvailableProcessors", "Arch", "HeapMemoryUsage")

        errors shouldBe empty
      }
      "report any errors in JMX queries" in new JmxMetricConfigurationFixture with JmxQueryFunctionalityFixture {

        val jmxQueryRsult = Set(
          new MBeanMetricResult(new RuntimeException("Something's gone wrong..."))
        )

        when(queryJmxBeansFnMock.apply(any[javautil.Map[ObjectName, javautil.Set[String]]])).thenReturn(jmxQueryRsult.asJava)

        val (results, errors) = collectMetrics(
          invalidJmxMbeansAndAttributes,
          expectedJmxMbeansAndMetricNames,
          expectedConfigWithObjectNames,
          Nil,
          queryJmxBeansFnMock
        )

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
    "getting JMX bean entities" should {
      "return errors in MBean names" in new JmxMetricConfigurationFixture {

        val (jmxMbeansAndAttributes, jmxMbeansAndMetricNames, configWithObjectNames, errorsFromConfigWithObjectNames) =
          getJmxMbeanEntities(configWithInvalidJmxQuery)

        jmxMbeansAndAttributes shouldBe Map.empty
        jmxMbeansAndMetricNames shouldBe Map.empty
        configWithObjectNames shouldBe Nil
        errorsFromConfigWithObjectNames should have size 1
      }
      "successfully return all beans and attributes" in new JmxMetricConfigurationFixture {

        val (jmxMbeansAndAttributes, jmxMbeansAndMetricNames, configWithObjectNames, errorsFromConfigWithObjectNames) =
          getJmxMbeanEntities(configWithValidMBeansAndAttributes)

        jmxMbeansAndAttributes shouldBe expectedJmxMbeansAndAttributes
        jmxMbeansAndMetricNames shouldBe expectedJmxMbeansAndMetricNames
        configWithObjectNames shouldBe expectedConfigWithObjectNames
        errorsFromConfigWithObjectNames shouldBe Nil
      }
    }
    "generating metrics" should {
      "generate all valid metrics" in new JmxMetricConfigurationFixture {
        private val numberOfCPUs = Runtime.getRuntime.availableProcessors.toLong

        val (results, errors) = generateMetrics(
          configWithValidMBeansAndAttributes,
          expectedJmxMbeansAndAttributes,
          expectedJmxMbeansAndMetricNames,
          expectedConfigWithObjectNames,
          Nil
        )

        results.size shouldBe 3

        results.find(_._1 == "jmx-os-mbean-AvailableProcessors") shouldBe Some(("jmx-os-mbean-AvailableProcessors", numberOfCPUs, EmptyTags, Counter))
        results.exists(_._1 == "jmx-memory-mbean-HeapMemoryUsage-committed") shouldBe true
        results.exists(_._1 == "jmx-memory-mbean-HeapMemoryUsage-max") shouldBe true

        errors.length shouldBe 1
      }
    }
    "generating metric definitions" should {
      "generate all valid metric definitions" in new JmxMetricConfigurationFixture {
        private val results = generateMetricDefinitions(configWithValidMBeansAndAttributes)

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
    val KafkaConsumerObjectName = new ObjectName("kafka.consumer:type=consumer-metrics,client-id=consumer-1")
    val KafkaProducerObjectName = new ObjectName("kafka.producer:type=producer-node-metrics,client-id=producer-1,node-id=node-1")
    val TestObjectNameWithWildcardInTheEndOfQuery = new ObjectName("kafka.consumer:type=consumer-metrics,client-id=*")
    val TestObjectNameWithWildcardInBetweenTheQuery = new ObjectName("kafka.consumer:type=*,client-id=*")
    val KafkaConsumerMetricName = "kafka-consumer-metric"

    val SimpleTestObjectName = new ObjectName("other-some-object-name", "key", "value")
    val OtherTestMetricName = "other-some-metric-name"

    val ObjectNamesWithoutWildcard = Map(
      KafkaConsumerObjectName -> KafkaConsumerMetricName,
      SimpleTestObjectName -> OtherTestMetricName
    )

    val ObjectNamesWithWildcardInTheEndOfQuery = Map(
      TestObjectNameWithWildcardInTheEndOfQuery -> KafkaConsumerMetricName,
      SimpleTestObjectName -> OtherTestMetricName
    )

    val ObjectNamesWithWildcardInBetweenTheQuery = Map(
      TestObjectNameWithWildcardInBetweenTheQuery -> KafkaConsumerMetricName,
      SimpleTestObjectName -> OtherTestMetricName
    )

    val ObjectNamesWithWildcardInValues = Map(
      new ObjectName("kafka.producer:type=producer-metrics,client-id=*") -> "kafka-producer1",
      new ObjectName("kafka.producer:type=producer-node-metrics,client-id=*,node-id=*") -> "kafka-producer2",
      SimpleTestObjectName -> OtherTestMetricName
    )
  }

  trait SimilarMetricNameFixture {
    val KafkaConsumerObjectName         = new ObjectName("kafka.consumer:type=consumer-fetch-manager-metrics,client-id=client-1")
    val KafkaConsumerExtendedObjectName = new ObjectName("kafka.consumer:type=consumer-fetch-manager-metrics,client-id=client-1,topic=test-topic")

    val ObjectNamesWithWildcardInValues = Map(
      new ObjectName("kafka.consumer:type=consumer-fetch-manager-metrics,client-id=*,topic=*") -> "kafka-consumer1",
      new ObjectName("kafka.consumer:type=consumer-fetch-manager-metrics,client-id=*") -> "kafka-consumer2"
    )
  }

  trait JmxQueryFunctionalityFixture extends MockitoSugar {
    protected val queryJmxBeansFnMock: javautil.Map[ObjectName, javautil.Set[String]] => javautil.Set[MBeanMetricResult] =
      mock[javautil.Map[ObjectName, javautil.Set[String]] => javautil.Set[MBeanMetricResult]]
  }

  trait JmxMetricConfigurationFixture {

    val EmptyTags: TagSet = TagSet.Empty

    protected val configWithInvalidJmxQuery: List[JmxMetricConfiguration] =
      JmxMetricConfiguration("os-mbean", "invalid-query",
        JmxMetricAttribute("AvailableProcessors", Counter) :: JmxMetricAttribute("Arch", Histogram) :: Nil
      ) :: Nil

    protected val invalidJmxMbeansAndAttributes = Map(
      new ObjectName("java.lang:type=OperatingSystem") -> Set("invalid-attribute"),
      new ObjectName("java.lang:type=Memory") -> Set("HeapMemoryUsage", "gc")
    )

    protected val configWithValidMBeansAndAttributes: List[JmxMetricConfiguration] =
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


    protected val expectedJmxMbeansAndAttributes = Map(
      new ObjectName("java.lang:type=OperatingSystem") -> Set("AvailableProcessors", "Arch"),
      new ObjectName("java.lang:type=Memory") -> Set("HeapMemoryUsage", "gc")
    )

    protected val expectedJmxMbeansAndMetricNames = Map(
      new ObjectName("java.lang:type=OperatingSystem") -> "os-mbean",
      new ObjectName("java.lang:type=Memory") -> "memory-mbean"
    )

    protected val expectedConfigWithObjectNames = List(
      ( "os-mbean",
        new ObjectName("java.lang:type=OperatingSystem"),
        List(JmxMetricAttribute("AvailableProcessors", Counter, Nil), JmxMetricAttribute("Arch", Histogram, Nil))
      ),
      ( "memory-mbean",
        new ObjectName("java.lang:type=Memory"),
        List(JmxMetricAttribute("HeapMemoryUsage", Counter, List("committed", "max")), JmxMetricAttribute("gc", Histogram, Nil))
      )
    )
  }
}
