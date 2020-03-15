package kamon.jmx.collector

import java.util.Properties

import akka.actor.ActorSystem
import kamon.Kamon
import kamon.prometheus.PrometheusReporter
import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord}
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, StringSerializer}
import org.scalactic.Prettifier
import org.scalatest.{Assertion, FlatSpec}
import org.scalatest.Matchers._
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.{MatchResult, Matcher}
import org.scalatest.time.{Minute, Seconds, Span}

import scala.collection.JavaConverters._

final class IncludeWords {
  def apply(parts: List[String]): Matcher[String] =
    new Matcher[String] {
      def apply(left: String): MatchResult =
        MatchResult(
          parts.forall(left contains _),
          s"String $left did not contain all parts ${parts.mkString}",
          s"String $left contained all parts  ${parts.mkString}",
          Vector(left, parts)
        )
      override def toString: String =
        "include (" + Prettifier.default(parts) + ")"
    }
}

class KamonJmxCollectorSpec extends FlatSpec with Eventually {
  val includeAll = new IncludeWords
  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(1, Minute)), interval = scaled(Span(5, Seconds)))

  def doSomeEntriesBeginWith(lines: List[String], beginWith: String): Assertion = atLeast(1, lines) should startWith(beginWith)

  def doSomeEntriesLookLike(lines: List[String],
                            prefix: String,
                            entries: List[String] = List.empty): Assertion = {
    val strings = lines.filter(_ startsWith prefix)
    atLeast(1, strings) should includeAll(entries)
  }

  "Kamon JMX collector" should "successfully collect JMX metrics and publish them to Kamon" in {

    val dockerBindHost = sys.env.getOrElse("DOCKER_BIND_HOST", "localhost")

    val testTopicName = "test"

    val kafkaConsumerProperties = new Properties()
    kafkaConsumerProperties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, s"$dockerBindHost:9092")
    kafkaConsumerProperties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, "test-group")
    kafkaConsumerProperties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, classOf[ByteArrayDeserializer].getName)
    kafkaConsumerProperties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, classOf[ByteArrayDeserializer].getName)

    val kafkaProducerProperties = new Properties()
    kafkaProducerProperties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, s"$dockerBindHost:9092")
    kafkaProducerProperties.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer].getName)
    kafkaProducerProperties.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer].getName)

    val producer = new KafkaProducer[String, String](kafkaProducerProperties)
    producer.send(new ProducerRecord[String, String](testTopicName, "some key", "some value"))

    val consumer = new KafkaConsumer[AnyRef, AnyRef](kafkaConsumerProperties)
    new KafkaConsumer[Integer, Integer](kafkaConsumerProperties)

    consumer.subscribe( (testTopicName :: Nil).asJava )
    consumer.poll(5000L)

    implicit val system: ActorSystem = ActorSystem()

    Kamon.init()
    KamonJmxMetricCollector()

    eventually {
      val metrics = HttpClient.getLinesFromURL("http://localhost:9095/")

      doSomeEntriesBeginWith(metrics, "jmx_os_mbean_AvailableProcessors")
      doSomeEntriesBeginWith(metrics, "jmx_os_memory_HeapMemoryUsage_committed")
      doSomeEntriesBeginWith(metrics, "jmx_os_memory_HeapMemoryUsage_max")
      doSomeEntriesBeginWith(metrics, "jmx_os_memory_ObjectPendingFinalizationCount")

      doSomeEntriesLookLike(metrics, "jmx_kafka_consumer_fetch_manager_topic_bytes_consumed_rate",
        List("""client_id="consumer-test-group-1""", """topic="test""", """type="consumer-fetch-manager-metrics"}""")
      )
      doSomeEntriesLookLike(metrics, "jmx_kafka_consumer_fetch_manager_records_lag_max",
        List("""client_id="consumer-test-group-1""" , """type="consumer-fetch-manager-metrics"""")
      )
      doSomeEntriesLookLike(metrics, "jmx_kafka_consumer_fetch_manager_records_lag_max",
        List("""client_id="consumer-test-group-2""", """type="consumer-fetch-manager-metrics""")
      )
      doSomeEntriesLookLike(metrics, "jmx_kafka_consumer_connection_count_bucket",
        List("""client_id="consumer-test-group-1""", """type="consumer-metrics""")
      )
      doSomeEntriesLookLike(metrics, "jmx_kafka_consumer_connection_count_bucket",
        List("""client_id="consumer-test-group-2""", """type="consumer-metrics""")
      )
      doSomeEntriesLookLike(metrics, "jmx_kafka_producer1_outgoing_byte_rate",
        List("""client_id="producer-1""", """type="producer-metrics""")
      )
      doSomeEntriesLookLike(metrics, "jmx_kafka_producer1_network_io_rate",
        List("""client_id="producer-1""", """type="producer-metrics""")
      )

      doSomeEntriesLookLike(metrics, "jmx_kafka_producer2_node_metrics_incoming_byte_rate",
        List("""type="producer-node-metrics""","""client_id="producer-1""","""node_id="node-1""")
        )
      doSomeEntriesLookLike(metrics, "jmx_kafka_producer2_node_metrics_outgoing_byte_rate",
        List("""type="producer-node-metrics""","""client_id="producer-1""","""node_id="node-1""")
        )
      doSomeEntriesLookLike(metrics, "jmx_kafka_producer2_node_metrics_incoming_byte_rate",
        List("""type="producer-node-metrics""","""client_id="producer-1""","""node_id="node--1""")
        )
      doSomeEntriesLookLike(metrics, "jmx_kafka_producer2_node_metrics_outgoing_byte_rate",
        List("""type="producer-node-metrics""","""client_id="producer-1""","""node_id="node--1""")
        )

      doSomeEntriesLookLike(metrics, "jmx_kafka_consumer_fetch_manager_topic_bytes_consumed_rate",
        List("""client_id="consumer-test-group-1""","""topic="test""","""type="consumer-fetch-manager-metrics""")
        )
      doSomeEntriesLookLike(metrics, "jmx_kafka_consumer_fetch_manager_records_lag_max",
        List("""client_id="consumer-test-group-1""","""type="consumer-fetch-manager-metrics""")
        )
      doSomeEntriesLookLike(metrics, "jmx_kafka_consumer_fetch_manager_records_lag_max",
        List("""client_id="consumer-test-group-2""","""type="consumer-fetch-manager-metrics""")
        )
    }
  }
}
