package kamon.jmx.collector

import akka.actor.{Actor, ActorLogging}
import kamon.jmx.collector.JmxMetricCollectorActor.{CollectMetrics, MetricsCollectionFinished}
import kamon.jmx.collector.SupportedKamonMetricTypes.{SupportedKamonMetric, SupportedKamonMetricType}

// TODO add tests
private[collector] class JmxMetricCollectorActor(metrics: List[(String, SupportedKamonMetric[_])],
                                                 configuration: List[JmxMetricConfiguration]) extends Actor with ActorLogging {
  import context.become

  override def receive: Receive = waiting

  private def waiting: Receive = {
    case CollectMetrics =>
      become(busy)
      log.debug(s"Received CollectMetrics")
      val (metricValues, errors) = MetricCollector.generateMetrics(configuration)

      for {
        (metricName, metricValue) <- metricValues
        ddd <- metrics.find(_._1 == metricName)
      } yield ddd._2.record(metricValue)

      errors.foreach { error =>
        log.error(error, "Failed to retrieve JMX metrics")
      }

      self ! MetricsCollectionFinished
    case _ =>
  }

  private def busy: Receive = {
    case CollectMetrics =>
      log.debug("Previous JMX log collection is still in progress, skipping...")
    case MetricsCollectionFinished =>
      log.debug("Metrics collection finished, becoming available...")
      become(waiting)
  }
}

object JmxMetricCollectorActor {
  sealed trait Message
  case object CollectMetrics
  case object MetricsCollectionFinished
}