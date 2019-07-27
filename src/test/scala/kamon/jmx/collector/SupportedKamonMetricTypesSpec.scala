package kamon.jmx.collector

import org.scalatest.WordSpec
import org.scalatest.Matchers._
import SupportedKamonMetricTypes._
import kamon.tag.TagSet
import org.scalatest.mockito.MockitoSugar
import org.mockito.Mockito.{verify, when}

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
          when(counterMetricMock.withTags(TagSet.Empty)).thenReturn(counterMetricMock)
          Counter.recordValue(counterMetricMock, 20L)
          verify(counterMetricMock).increment(20L)
        }
        "support a Histogram type" in new MetricRecordingFixture {
          when(histogramMetricMock.withTags(TagSet.Empty)).thenReturn(histogramMetricMock)
          Histogram.recordValue(histogramMetricMock, 20L)
          verify(histogramMetricMock).record(20L)
        }
        "support an IncrementingGauge type" in new MetricRecordingFixture {
          when(gaugeMetricMock.withTags(TagSet.Empty)).thenReturn(gaugeMetricMock)
          IncrementingGauge.recordValue(gaugeMetricMock, 20L)
          verify(gaugeMetricMock).increment(20L)
        }
        "support a DecrementingGauge type" in new MetricRecordingFixture {
          when(gaugeMetricMock.withTags(TagSet.Empty)).thenReturn(gaugeMetricMock)
          DecrementingGauge.recordValue(gaugeMetricMock, 20L)
          verify(gaugeMetricMock).decrement(20L)
        }
        "support a PunctualGauge type" in new MetricRecordingFixture {
          when(gaugeMetricMock.withTags(TagSet.Empty)).thenReturn(gaugeMetricMock)
          PunctualGauge.recordValue(gaugeMetricMock, 20L)
          verify(gaugeMetricMock).update(20L)
        }
      }
      "uses tags" should {
        "support a Counter type" in new MetricRecordingFixture {
          when(counterMetricMock.withTags(testTags)).thenReturn(counterMetricMock)
          Counter.recordValue(counterMetricMock, 20L, testTags)
          verify(counterMetricMock).increment(20L)
        }
        "support a Histogram type" in new MetricRecordingFixture {
          when(histogramMetricMock.withTags(testTags)).thenReturn(histogramMetricMock)
          Histogram.recordValue(histogramMetricMock, 20L, testTags)
          verify(histogramMetricMock).record(20L)
        }
        "support an IncrementingGauge type" in new MetricRecordingFixture {
          when(gaugeMetricMock.withTags(testTags)).thenReturn(gaugeMetricMock)
          IncrementingGauge.recordValue(gaugeMetricMock, 20L, testTags)
          verify(gaugeMetricMock).increment(20L)
        }
        "support a DecrementingGauge type" in new MetricRecordingFixture {
          when(gaugeMetricMock.withTags(testTags)).thenReturn(gaugeMetricMock)
          DecrementingGauge.recordValue(gaugeMetricMock, 20L, testTags)
          verify(gaugeMetricMock).decrement(20L)
        }
        "support a PunctualGauge type" in new MetricRecordingFixture {
          when(gaugeMetricMock.withTags(testTags)).thenReturn(gaugeMetricMock)
          PunctualGauge.recordValue(gaugeMetricMock, 20L, testTags)
          verify(gaugeMetricMock).update(20L)
        }
      }
    }
  }

  trait SupportedKamonMetricTypeFixture extends SupportedKamonMetricType with MockitoSugar {

    var passedMetricName: String = ""

    val counterMetricMock = mock[kamon.metric.Counter]

    override type T = kamon.metric.Counter
    override protected def getMetricInstrument(metricName: String): T = {
      passedMetricName = metricName
      counterMetricMock
    }
    override def recordValue(metricInstrument: T, value: Long, tags: TagSet = TagSet.Empty): Unit =
      metricInstrument.increment(value)
  }

  trait MetricRecordingFixture extends MockitoSugar {
    val counterMetricMock = mock[kamon.metric.Counter]
    val histogramMetricMock = mock[kamon.metric.Histogram]
    val gaugeMetricMock = mock[kamon.metric.Gauge]
    val rangeSamplerMetricMock = mock[kamon.metric.RangeSampler]

    val testTags: TagSet = TagSet.from(Map("some tag key" -> "some tag value"))
  }
}
