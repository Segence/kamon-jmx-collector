package kamon.jmx.collector

import kamon.Kamon
import kamon.metric.{CounterMetric, GaugeMetric, HistogramMetric, Metric}

private[collector] object SupportedKamonMetricTypes {

  trait SupportedKamonMetricType {
    type T <: Metric[_]
    def getMetricInstrument(metricName: String): T
    def recordValue(metricInstrument: T, value: Long): Unit

    def record(metricName: String, value: Long): Unit = recordValue(
      getMetricInstrument(metricName),
      value
    )
  }

  case object Counter extends SupportedKamonMetricType {
    override type T = CounterMetric
    override def getMetricInstrument(metricName: String): CounterMetric = Kamon.counter(metricName)
    override def recordValue(metricInstrument: CounterMetric, value: Long): Unit = metricInstrument.increment(value)
  }

  case object Histogram extends SupportedKamonMetricType {
    override type T = HistogramMetric
    override def getMetricInstrument(metricName: String): HistogramMetric = Kamon.histogram(metricName)
    override def recordValue(metricInstrument: HistogramMetric, value: Long): Unit = metricInstrument.record(value)
  }

  trait Gauge extends SupportedKamonMetricType {
    override type T = GaugeMetric
    override def getMetricInstrument(metricName: String): GaugeMetric = Kamon.gauge(metricName)
  }

  case object IncrementingGauge extends Gauge {
    override def recordValue(metricInstrument: GaugeMetric, value: Long): Unit = metricInstrument.increment(value)
  }

  case object DecrementingGauge extends Gauge {
    override def recordValue(metricInstrument: GaugeMetric, value: Long): Unit = metricInstrument.decrement(value)
  }

  case object PunctualGauge extends Gauge {
    override def recordValue(metricInstrument: GaugeMetric, value: Long): Unit = metricInstrument.set(value)
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
