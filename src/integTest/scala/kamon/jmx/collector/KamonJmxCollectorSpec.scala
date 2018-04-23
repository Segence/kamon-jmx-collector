package kamon.jmx.collector

import akka.actor.ActorSystem
import kamon.Kamon
import kamon.prometheus.PrometheusReporter
import org.scalatest.FlatSpec
import org.scalatest.Matchers._
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Minute, Seconds, Span}

class KamonJmxCollectorSpec extends FlatSpec with Eventually {

  implicit override val patienceConfig =
    PatienceConfig(timeout = scaled(Span(1, Minute)), interval = scaled(Span(5, Seconds)))

  def doSomeEntriesBeginWith(lines: List[String], beginWith: String): Boolean = lines.exists(_.startsWith(beginWith))

  "Kamon" should "" in {

    implicit val system = ActorSystem()

    Kamon.addReporter(new PrometheusReporter())
    KamonJmxMetricCollector()

    eventually {
      val metrics = HttpClient.getLinesFromURL("http://localhost:9095/")

      doSomeEntriesBeginWith(metrics, "jmx_os_mbean_AvailableProcessors") shouldBe true
      doSomeEntriesBeginWith(metrics, "jmx_os_memory_HeapMemoryUsage_committed") shouldBe true
      doSomeEntriesBeginWith(metrics, "jmx_os_memory_HeapMemoryUsage_max") shouldBe true
      doSomeEntriesBeginWith(metrics, "jmx_os_memory_ObjectPendingFinalizationCount") shouldBe true
    }
  }
}
