package kamon.jmx.collector

import java.util.Properties

import akka.actor.ActorSystem
import kamon.Kamon
import kamon.prometheus.PrometheusReporter
import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord}
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, StringSerializer}
import org.scalatest.FlatSpec
import org.scalatest.Matchers._
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Minute, Seconds, Span}

class KamonJmxCollectorSpec extends FlatSpec with Eventually {

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(1, Minute)), interval = scaled(Span(5, Seconds)))

  def doSomeEntriesBeginWith(lines: List[String], beginWith: String): Boolean = lines.exists(_.startsWith(beginWith))

  "Kamon JMX collector" should "successfully collect JMX metrics and publish them to Kamon" in {

    val dockerBindHost = sys.env.get("DOCKER_BIND_HOST").getOrElse("localhost")

    val kafkaConsumerProperties = new Properties()
    kafkaConsumerProperties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
    kafkaConsumerProperties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, classOf[ByteArrayDeserializer].getName)
    kafkaConsumerProperties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, classOf[ByteArrayDeserializer].getName)

    val kafkaProducerProperties = new Properties()
    kafkaProducerProperties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, s"$dockerBindHost:9092")
    kafkaProducerProperties.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer].getName)
    kafkaProducerProperties.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer].getName)

    new KafkaConsumer[AnyRef, AnyRef](kafkaConsumerProperties)
    new KafkaConsumer[Integer, Integer](kafkaConsumerProperties)

    val producer = new KafkaProducer[String, String](kafkaProducerProperties)
    producer.send(new ProducerRecord[String, String]("test", "some key", "some value"))

    implicit val system: ActorSystem = ActorSystem()

    Kamon.addReporter(new PrometheusReporter())
    KamonJmxMetricCollector()

    eventually {
      val metrics = HttpClient.getLinesFromURL("http://localhost:9095/")

      doSomeEntriesBeginWith(metrics, "jmx_os_mbean_AvailableProcessors") shouldBe true
      doSomeEntriesBeginWith(metrics, "jmx_os_memory_HeapMemoryUsage_committed") shouldBe true
      doSomeEntriesBeginWith(metrics, "jmx_os_memory_HeapMemoryUsage_max") shouldBe true
      doSomeEntriesBeginWith(metrics, "jmx_os_memory_ObjectPendingFinalizationCount") shouldBe true
      doSomeEntriesBeginWith(metrics, "jmx_kafka_consumer_consumer_1_connection_count") shouldBe true
      doSomeEntriesBeginWith(metrics, "jmx_kafka_consumer_consumer_2_connection_count") shouldBe true
      doSomeEntriesBeginWith(metrics, "jmx_kafka_producer1_producer_1_outgoing_byte_rate") shouldBe true
      doSomeEntriesBeginWith(metrics, "jmx_kafka_producer1_producer_1_network_io_rate") shouldBe true
      doSomeEntriesBeginWith(metrics, "jmx_kafka_producer2_node_metrics_node_1_producer_1_incoming_byte_rate") shouldBe true
      doSomeEntriesBeginWith(metrics, "jmx_kafka_producer2_node_metrics_node_1_producer_1_outgoing_byte_rate") shouldBe true
      doSomeEntriesBeginWith(metrics, "jmx_kafka_producer2_node_metrics_node__1_producer_1_incoming_byte_rate") shouldBe true
      doSomeEntriesBeginWith(metrics, "jmx_kafka_producer2_node_metrics_node__1_producer_1_outgoing_byte_rate") shouldBe true
    }
  }
}
