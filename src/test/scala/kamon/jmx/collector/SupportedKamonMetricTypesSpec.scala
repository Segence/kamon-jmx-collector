package kamon.jmx.collector

import org.scalatest.WordSpec
import org.scalatest.Matchers._
import SupportedKamonMetricTypes._
import kamon.metric.{CounterMetric, GaugeMetric, HistogramMetric}
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
      "successfully parse an IncrementingGauge type" in {
        parse("incrementing-gauge") shouldBe IncrementingGauge
      }
      "successfully parse a DecrementingGauge type" in {
        parse("decrementing-gauge") shouldBe DecrementingGauge
      }
      "successfully parse a PunctualGauge type" in {
        parse("punctual-gauge") shouldBe PunctualGauge
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
      "support an IncrementingGauge type" in new MetricRecordingFixture {
        IncrementingGauge.recordValue(gaugeMetricMock, 20L)
        verify(gaugeMetricMock).increment(20L)
      }
      "support a DecrementingGauge type" in new MetricRecordingFixture {
        DecrementingGauge.recordValue(gaugeMetricMock, 20L)
        verify(gaugeMetricMock).decrement(20L)
      }
      "support a PunctualGauge type" in new MetricRecordingFixture {
        PunctualGauge.recordValue(gaugeMetricMock, 20L)
        verify(gaugeMetricMock).set(20L)
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
    val gaugeMetricMock = mock[GaugeMetric]
  }
}
