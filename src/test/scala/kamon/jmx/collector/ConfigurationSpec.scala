package kamon.jmx.collector

import com.typesafe.config.ConfigFactory
import kamon.jmx.collector.SupportedKamonMetricTypes.{Counter, Histogram}
import org.scalatest.FlatSpec
import scala.concurrent.duration._
import org.scalatest.Matchers._

class ConfigurationSpec extends FlatSpec {
  "A configuration parser" should "parse valid config" in new Configuration {

    val expectedResult = JmxCollectorConfiguration(
      1 second, 2 seconds,
      JmxMetricConfiguration(
        "my-mbean",
        "test:type=exampleBean,name=*",
        JmxMetricAttribute("Value1", Counter, "composite-data-attribute1" :: "composite-data-attribute2" :: Nil) ::
        JmxMetricAttribute("Value2", Histogram, Nil) ::
        Nil
      ) :: Nil
    )

    private val testConfigURL = getClass.getResource("/config1.conf")
    private val config = ConfigFactory.parseURL(testConfigURL)

    parseConfiguration(config) shouldBe expectedResult
  }
}
