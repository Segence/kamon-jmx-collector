package kamon.jmx.collector

import kamon.Kamon
import kamon.metric._

private[collector] object SupportedKamonMetricTypes {

  case class SupportedKamonMetric[T](metricInstrument: Metric[T]) {
    def record(value: Long): Unit = metricInstrument match {
      case counter: CounterMetric => counter.increment(value)
      case histogram: HistogramMetric => histogram.record(value)
      case _ =>
    }
  }

  sealed trait SupportedKamonMetricType {
    type T
    def registerMetric(metricName: String): Metric[T]
  }

  case object Counter extends SupportedKamonMetricType {
    override type T = Counter
    override def registerMetric(metricName: String): Metric[T] = Kamon.counter(metricName)
  }

  case object Histogram extends SupportedKamonMetricType {
    override type T = Histogram
    override def registerMetric(metricName: String): Metric[T] = Kamon.histogram(metricName)
  }

  def parse: PartialFunction[String, SupportedKamonMetricType] = {
    case "counter" => Counter
    case "histogram" => Histogram
    case invalidMetricType => throw new IllegalArgumentException(s"Provided metric type[$invalidMetricType] is not valid")
  }
}
