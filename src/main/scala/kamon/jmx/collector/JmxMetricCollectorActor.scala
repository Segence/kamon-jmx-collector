package kamon.jmx.collector

import akka.actor.{Actor, ActorLogging}
import kamon.jmx.collector.JmxMetricCollectorActor.CollectMetrics

private[collector] class JmxMetricCollectorActor(configuration: List[JmxMetricConfiguration]) extends Actor with ActorLogging {
  override def receive: Receive = waiting

  private def waiting: Receive = {
    case CollectMetrics =>
      val (metrics, errors) = MetricCollector.generateMetrics(configuration)
  }
}

object JmxMetricCollectorActor {
  sealed trait Message
  case object CollectMetrics
}