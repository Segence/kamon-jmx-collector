package kamon.jmx.collector

import org.scalatest.WordSpec
import org.scalatest.Matchers._
import SupportedKamonMetricTypes.{Counter, Histogram, SupportedKamonMetric, parse}
import kamon.metric.{CounterMetric, HistogramMetric}
import org.scalatest.mockito.MockitoSugar
import org.mockito.Mockito.verify

class SupportedKamonMetricTypesSpec extends WordSpec {
  "The supported Kamon metric types object" when {
    "parsing a String as a metric type" should {
      "throw an exception when metric type in invalid" in {
        assertThrows[IllegalArgumentException] {
          parse("invalid")
        }
      }
      "successfully parse a Counter type" in {
        parse("counter") shouldBe Counter
      }
      "successfully parse a Histogram type" in {
        parse("histogram") shouldBe Histogram
      }
    }
    "recording a metric" should {
      "support a Counter type" in new MetricRecordingFixture {
        SupportedKamonMetric(counterMetricMock).record(20L)
        verify(counterMetricMock).increment(20L)
      }
      "support a Histogram type" in new MetricRecordingFixture {
        SupportedKamonMetric(histogramMetricMock).record(20L)
        verify(histogramMetricMock).record(20L)
      }
    }
  }

  trait MetricRecordingFixture extends MockitoSugar {
    val counterMetricMock = mock[CounterMetric]
    val histogramMetricMock = mock[HistogramMetric]
  }
}
