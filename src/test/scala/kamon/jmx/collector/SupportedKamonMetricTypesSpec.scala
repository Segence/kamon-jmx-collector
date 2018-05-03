package kamon.jmx.collector

import org.scalatest.WordSpec
import org.scalatest.Matchers._
import SupportedKamonMetricTypes.{Counter, Histogram, SupportedKamonMetricType, parse}
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
    "recording a generic metric value" should {
      "successfully record the given value" in new SupportedKamonMetricTypeFixture {
        record("some-metric-name", 10L)

        passedMetricName shouldBe "some-metric-name"
        verify(counterMetricMock).increment(10L)
      }
    }
    "recording a specific metric value" should {
      "support a Counter type" in new MetricRecordingFixture {
        Counter.recordValue(counterMetricMock, 20L)
        verify(counterMetricMock).increment(20L)
      }
      "support a Histogram type" in new MetricRecordingFixture {
        Histogram.recordValue(histogramMetricMock, 20L)
        verify(histogramMetricMock).record(20L)
      }
    }
  }

  trait SupportedKamonMetricTypeFixture extends SupportedKamonMetricType with MockitoSugar {

    var passedMetricName: String = ""

    val counterMetricMock = mock[CounterMetric]

    override type T = CounterMetric
    override def getMetricInstrument(metricName: String): CounterMetric = {
      passedMetricName = metricName
      counterMetricMock
    }
    override def recordValue(metricInstrument: CounterMetric, value: Long): Unit = metricInstrument.increment(value)
  }

  trait MetricRecordingFixture extends MockitoSugar {
    val counterMetricMock = mock[CounterMetric]
    val histogramMetricMock = mock[HistogramMetric]
  }
}
