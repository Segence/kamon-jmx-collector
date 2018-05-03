package kamon.jmx.collector

import kamon.jmx.collector.MetricCollector.FullMetricName
import org.scalatest.FlatSpec
import org.scalatest.Matchers._

class FullMetricNameSpec extends FlatSpec {
  "A full metric name object" should "have a string representation of metric name only if no suffix is present" in {
    FullMetricName("aaa").toString shouldBe "aaa"
  }

  it should "have a string representation of a metric name and suffix if a valid suffix is present" in {
    FullMetricName("aaa", Some("bbb")).toString shouldBe "aaa-bbb"
  }
}
