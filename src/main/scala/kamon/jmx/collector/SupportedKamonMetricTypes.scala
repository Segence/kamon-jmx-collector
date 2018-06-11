package kamon.jmx.collector

import kamon.{Kamon, Tags}
import kamon.metric.{Metric, CounterMetric, HistogramMetric, GaugeMetric}

private[collector] object SupportedKamonMetricTypes {

  trait SupportedKamonMetricType {
    type T <: Metric[_]
    protected def getMetricInstrument(metricName: String): T
    private[collector] def recordValue(metricInstrument: T, value: Long, tags: Tags = Map.empty): Unit

    def record(metricName: String, value: Long, tags: Tags = Map.empty): Unit = recordValue(getMetricInstrument(metricName), value, tags)
  }

  case object Counter extends SupportedKamonMetricType {
    override type T = CounterMetric
    override protected def getMetricInstrument(metricName: String): CounterMetric = Kamon.counter(metricName)
    override private[collector] def recordValue(metricInstrument: CounterMetric, value: Long, tags: Tags = Map.empty): Unit =
      metricInstrument.refine(tags).increment(value)
  }

  case object Histogram extends SupportedKamonMetricType {
    override type T = HistogramMetric
    override protected def getMetricInstrument(metricName: String): HistogramMetric = Kamon.histogram(metricName)
    override private[collector] def recordValue(metricInstrument: HistogramMetric, value: Long, tags: Tags = Map.empty): Unit =
      metricInstrument.refine(tags).record(value)
  }

  trait Gauge extends SupportedKamonMetricType {
    override type T = GaugeMetric
    override protected def getMetricInstrument(metricName: String): GaugeMetric = Kamon.gauge(metricName)
  }

  case object IncrementingGauge extends Gauge {
    override private[collector] def recordValue(metricInstrument: GaugeMetric, value: Long, tags: Tags = Map.empty): Unit =
      metricInstrument.refine(tags).increment(value)
  }

  case object DecrementingGauge extends Gauge {
    override private[collector] def recordValue(metricInstrument: GaugeMetric, value: Long, tags: Tags = Map.empty): Unit =
      metricInstrument.refine(tags).decrement(value)
  }

  case object PunctualGauge extends Gauge {
    override private[collector] def recordValue(metricInstrument: GaugeMetric, value: Long, tags: Tags = Map.empty): Unit =
      metricInstrument.refine(tags).set(value)
  }

  def parse: PartialFunction[String, SupportedKamonMetricType] = {
    case "counter" => Counter
    case "histogram" => Histogram
    case "incrementing-gauge" => IncrementingGauge
    case "decrementing-gauge" => DecrementingGauge
    case "punctual-gauge" => PunctualGauge
    case invalidMetricType => throw new IllegalArgumentException(s"Provided metric type[$invalidMetricType] is not valid")
  }
}
