package kamon.jmx.collector

import kamon.Kamon
import kamon.metric.{CounterMetric, HistogramMetric}

private[collector] object SupportedKamonMetricTypes {

  sealed trait SupportedKamonMetricType[T] {
    def registerMetric(metricName: String): T
  }

  case object Counter extends SupportedKamonMetricType[CounterMetric] {
    override def registerMetric(metricName: String): CounterMetric = Kamon.counter(metricName)
  }

  case object Histogram extends SupportedKamonMetricType[HistogramMetric] {
    override def registerMetric(metricName: String): HistogramMetric = Kamon.histogram(metricName)
  }

  def parse: PartialFunction[String, SupportedKamonMetricType[_]] = {
    case "counter" => Counter
    case "histogram" => Histogram
    case invalidMetricType => throw new IllegalArgumentException(s"Provided metric type[$invalidMetricType] is not valid")
  }
}