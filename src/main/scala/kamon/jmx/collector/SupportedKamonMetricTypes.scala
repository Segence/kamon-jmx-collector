package kamon.jmx.collector

import kamon.Kamon
import kamon.metric.Instrument
import kamon.tag.TagSet

private[collector] object SupportedKamonMetricTypes {

  trait SupportedKamonMetricType {
    type T <: Instrument[_, _]
    protected def getMetricInstrument(metricName: String): T
    private[collector] def recordValue(metricInstrument: T, value: Long, tags: TagSet = TagSet.Empty): Unit

    def record(metricName: String, value: Long, tags: TagSet = TagSet.Empty): Unit = recordValue(getMetricInstrument(metricName), value, tags)
  }

  case object Counter extends SupportedKamonMetricType {
    override type T = kamon.metric.Counter
    override protected def getMetricInstrument(metricName: String): T = Kamon.counter(metricName).withoutTags()
    override private[collector] def recordValue(metricInstrument: T, value: Long, tags: TagSet = TagSet.Empty): Unit =
      metricInstrument.withTags(tags).increment(value)
  }

  case object Histogram extends SupportedKamonMetricType {
    override type T = kamon.metric.Histogram
    override protected def getMetricInstrument(metricName: String): T = Kamon.histogram(metricName).withoutTags()
    override private[collector] def recordValue(metricInstrument: T, value: Long, tags: TagSet = TagSet.Empty): Unit =
      metricInstrument.withTags(tags).record(value)
  }

  trait Gauge extends SupportedKamonMetricType {
    override type T = kamon.metric.Gauge
    override protected def getMetricInstrument(metricName: String): T = Kamon.gauge(metricName).withoutTags()
  }

  trait RangeSampler extends SupportedKamonMetricType {
    override type T = kamon.metric.RangeSampler
    override protected def getMetricInstrument(metricName: String): T = Kamon.rangeSampler(metricName).withoutTags()
  }

  case object IncrementingRangeSampler extends RangeSampler {
    override private[collector] def recordValue(metricInstrument: T, value: Long, tags: TagSet = TagSet.Empty): Unit =
      metricInstrument.withTags(tags).increment(value)
  }

  case object DecrementingRangeSampler extends RangeSampler {
    override private[collector] def recordValue(metricInstrument: T, value: Long, tags: TagSet = TagSet.Empty): Unit =
      metricInstrument.withTags(tags).decrement(value)
  }

  case object PunctualGauge extends Gauge {
    override private[collector] def recordValue(metricInstrument: T, value: Long, tags: TagSet = TagSet.Empty): Unit =
      metricInstrument.withTags(tags).update(value)
  }

  def parse: PartialFunction[String, SupportedKamonMetricType] = {
    case "counter" => Counter
    case "histogram" => Histogram
    case "incrementing-range-sampler" => IncrementingRangeSampler
    case "decrementing-range-sampler" => DecrementingRangeSampler
    case "punctual-gauge" => PunctualGauge
    case invalidMetricType => throw new IllegalArgumentException(s"Provided metric type[$invalidMetricType] is not valid")
  }
}
