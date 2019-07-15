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
      "successfully parse an IncrementingRangeSampler type" in {
        parse("incrementing-range-sampler") shouldBe IncrementingRangeSampler
      }
      "successfully parse a DecrementingRangeSampler type" in {
        parse("decrementing-range-sampler") shouldBe DecrementingRangeSampler
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
        "support an IncrementingRangeSampler type" in new MetricRecordingFixture {
          when(rangeSamplerMetricMock.withTags(TagSet.Empty)).thenReturn(rangeSamplerMetricMock)
          IncrementingRangeSampler.recordValue(rangeSamplerMetricMock, 20L)
          verify(rangeSamplerMetricMock).increment(20L)
        }
        "support a DecrementingRangeSampler type" in new MetricRecordingFixture {
          when(rangeSamplerMetricMock.withTags(TagSet.Empty)).thenReturn(rangeSamplerMetricMock)
          DecrementingRangeSampler.recordValue(rangeSamplerMetricMock, 20L)
          verify(rangeSamplerMetricMock).decrement(20L)
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
        "support an IncrementingRangeSampler type" in new MetricRecordingFixture {
          when(rangeSamplerMetricMock.withTags(testTags)).thenReturn(rangeSamplerMetricMock)
          IncrementingRangeSampler.recordValue(rangeSamplerMetricMock, 20L, testTags)
          verify(rangeSamplerMetricMock).increment(20L)
        }
        "support a DecrementingRangeSampler type" in new MetricRecordingFixture {
          when(rangeSamplerMetricMock.withTags(testTags)).thenReturn(rangeSamplerMetricMock)
          DecrementingRangeSampler.recordValue(rangeSamplerMetricMock, 20L, testTags)
          verify(rangeSamplerMetricMock).decrement(20L)
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

    val counterMetricMock = {
      val m = mock[kamon.metric.Counter]
      m
    }

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
