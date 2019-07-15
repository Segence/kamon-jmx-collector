package kamon.jmx.collector

import akka.actor.{Actor, ActorLogging}
import kamon.jmx.collector.JmxMetricCollectorActor.{CollectMetrics, MetricsCollectionFinished}
import kamon.jmx.collector.SupportedKamonMetricTypes.SupportedKamonMetricType
import kamon.tag.TagSet

private[collector] class JmxMetricCollectorActor(
    collectMetrics: () => (List[(String, Long, TagSet, SupportedKamonMetricType)], List[Throwable])
  ) extends Actor with ActorLogging {

    import context.become

    override def receive: Receive = waiting

    private def waiting: Receive = {
      case CollectMetrics =>
        become(busy)
        log.debug(s"Received CollectMetrics")

        val (metricValues, errors) = collectMetrics()

        for {
          (metricName, metricValue, metricTags, metricType) <- metricValues
        } yield metricType.record(metricName, metricValue, metricTags)

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

private[collector] object JmxMetricCollectorActor {
  sealed trait Message
  case object CollectMetrics
  case object MetricsCollectionFinished
}
