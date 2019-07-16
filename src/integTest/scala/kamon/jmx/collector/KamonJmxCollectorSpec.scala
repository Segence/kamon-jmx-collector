package kamon.jmx.collector

import java.util.Properties

import akka.actor.ActorSystem
import kamon.Kamon
import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord}
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, StringSerializer}
import org.scalatest.{Assertion, FlatSpec}
import org.scalatest.Matchers._
import org.scalatest.Inspectors._
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Minute, Seconds, Span}
import scala.collection.JavaConverters._

class KamonJmxCollectorSpec extends FlatSpec with Eventually {

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(1, Minute)), interval = scaled(Span(5, Seconds)))

  def doSomeEntriesBeginWith(lines: List[String], beginWith: String): Assertion = forAll(lines) { line => line should startWith(beginWith) }

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

      doSomeEntriesBeginWith(metrics, "jmx_os_mbean_AvailableProcessors") shouldBe true
      doSomeEntriesBeginWith(metrics, "jmx_os_memory_HeapMemoryUsage_committed") shouldBe true
      doSomeEntriesBeginWith(metrics, "jmx_os_memory_HeapMemoryUsage_max") shouldBe true
      doSomeEntriesBeginWith(metrics, "jmx_os_memory_ObjectPendingFinalizationCount") shouldBe true

      doSomeEntriesBeginWith(metrics, """jmx_kafka_consumer_connection_count_bucket{type="consumer-metrics",client_id="consumer-1"""") shouldBe true
      doSomeEntriesBeginWith(metrics, """jmx_kafka_consumer_connection_count_bucket{type="consumer-metrics",client_id="consumer-2"""") shouldBe true

      doSomeEntriesBeginWith(metrics, """jmx_kafka_producer1_outgoing_byte_rate{client_id="producer-1",type="producer-metrics"}""") shouldBe true
      doSomeEntriesBeginWith(metrics, """jmx_kafka_producer1_network_io_rate{client_id="producer-1",type="producer-metrics"}""") shouldBe true

      doSomeEntriesBeginWith(metrics, """jmx_kafka_producer2_node_metrics_incoming_byte_rate{type="producer-node-metrics",node_id="node-1",client_id="producer-1"}""") shouldBe true
      doSomeEntriesBeginWith(metrics, """jmx_kafka_producer2_node_metrics_outgoing_byte_rate{type="producer-node-metrics",node_id="node-1",client_id="producer-1"}""") shouldBe true
      doSomeEntriesBeginWith(metrics, """jmx_kafka_producer2_node_metrics_incoming_byte_rate{type="producer-node-metrics",node_id="node--1",client_id="producer-1"}""") shouldBe true
      doSomeEntriesBeginWith(metrics, """jmx_kafka_producer2_node_metrics_outgoing_byte_rate{type="producer-node-metrics",node_id="node--1",client_id="producer-1"}""") shouldBe true

      doSomeEntriesBeginWith(metrics, """jmx_kafka_consumer_fetch_manager_topic_bytes_consumed_rate{topic="test",type="consumer-fetch-manager-metrics",client_id="consumer-1"}""") shouldBe true
      doSomeEntriesBeginWith(metrics, """jmx_kafka_consumer_fetch_manager_records_lag_max{type="consumer-fetch-manager-metrics",client_id="consumer-1"}""") shouldBe true
      doSomeEntriesBeginWith(metrics, """jmx_kafka_consumer_fetch_manager_records_lag_max{type="consumer-fetch-manager-metrics",client_id="consumer-2"}""") shouldBe true
    }
  }
}
