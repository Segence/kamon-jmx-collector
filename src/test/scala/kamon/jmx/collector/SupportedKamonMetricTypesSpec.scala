package kamon.jmx.collector

import org.scalatest.WordSpec
import org.scalatest.Matchers._
import SupportedKamonMetricTypes._
import kamon.Tags
import kamon.metric.{CounterMetric, GaugeMetric, HistogramMetric}
import org.scalatest.mockito.MockitoSugar
import org.mockito.Mockito.{when, verify}

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
    "recording a specific metric value" that {
      "uses no tags" should {
        "support a Counter type" in new MetricRecordingFixture {
          when(counterMetricMock.refine(Map.empty[String, String])).thenReturn(counterMetricMock)
          Counter.recordValue(counterMetricMock, 20L)
          verify(counterMetricMock).increment(20L)
        }
        "support a Histogram type" in new MetricRecordingFixture {
          when(histogramMetricMock.refine(Map.empty[String, String])).thenReturn(histogramMetricMock)
          Histogram.recordValue(histogramMetricMock, 20L)
          verify(histogramMetricMock).record(20L)
        }
        "support an IncrementingGauge type" in new MetricRecordingFixture {
          when(gaugeMetricMock.refine(Map.empty[String, String])).thenReturn(gaugeMetricMock)
          IncrementingGauge.recordValue(gaugeMetricMock, 20L)
          verify(gaugeMetricMock).increment(20L)
        }
        "support a DecrementingGauge type" in new MetricRecordingFixture {
          when(gaugeMetricMock.refine(Map.empty[String, String])).thenReturn(gaugeMetricMock)
          DecrementingGauge.recordValue(gaugeMetricMock, 20L)
          verify(gaugeMetricMock).decrement(20L)
        }
        "support a PunctualGauge type" in new MetricRecordingFixture {
          when(gaugeMetricMock.refine(Map.empty[String, String])).thenReturn(gaugeMetricMock)
          PunctualGauge.recordValue(gaugeMetricMock, 20L)
          verify(gaugeMetricMock).set(20L)
        }
      }
      "uses tags" should {
        "support a Counter type" in new MetricRecordingFixture {
          when(counterMetricMock.refine(testTags)).thenReturn(counterMetricMock)
          Counter.recordValue(counterMetricMock, 20L, testTags)
          verify(counterMetricMock).increment(20L)
        }
        "support a Histogram type" in new MetricRecordingFixture {
          when(histogramMetricMock.refine(testTags)).thenReturn(histogramMetricMock)
          Histogram.recordValue(histogramMetricMock, 20L, testTags)
          verify(histogramMetricMock).record(20L)
        }
        "support an IncrementingGauge type" in new MetricRecordingFixture {
          when(gaugeMetricMock.refine(testTags)).thenReturn(gaugeMetricMock)
          IncrementingGauge.recordValue(gaugeMetricMock, 20L, testTags)
          verify(gaugeMetricMock).increment(20L)
        }
        "support a DecrementingGauge type" in new MetricRecordingFixture {
          when(gaugeMetricMock.refine(testTags)).thenReturn(gaugeMetricMock)
          DecrementingGauge.recordValue(gaugeMetricMock, 20L, testTags)
          verify(gaugeMetricMock).decrement(20L)
        }
        "support a PunctualGauge type" in new MetricRecordingFixture {
          when(gaugeMetricMock.refine(testTags)).thenReturn(gaugeMetricMock)
          PunctualGauge.recordValue(gaugeMetricMock, 20L, testTags)
          verify(gaugeMetricMock).set(20L)
        }
      }
    }
  }

  trait SupportedKamonMetricTypeFixture extends SupportedKamonMetricType with MockitoSugar {

    var passedMetricName: String = ""

    val counterMetricMock = mock[CounterMetric]

    override type T = CounterMetric
    override protected def getMetricInstrument(metricName: String): CounterMetric = {
      passedMetricName = metricName
      counterMetricMock
    }
    override def recordValue(metricInstrument: CounterMetric, value: Long, tags: Tags = Map.empty): Unit =
      metricInstrument.increment(value)
  }

  trait MetricRecordingFixture extends MockitoSugar {
    val counterMetricMock = mock[CounterMetric]
    val histogramMetricMock = mock[HistogramMetric]
    val gaugeMetricMock = mock[GaugeMetric]

    val testTags = Map("some tag key" -> "some tag value")
  }
}
