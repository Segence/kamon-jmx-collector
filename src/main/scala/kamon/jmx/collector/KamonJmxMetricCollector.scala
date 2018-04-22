package kamon.jmx.collector

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory
import kamon.jmx.collector.JmxMetricCollectorActor.CollectMetrics
import kamon.jmx.collector.SupportedKamonMetricTypes.SupportedKamonMetric

import scala.concurrent.ExecutionContext

object KamonJmxMetricCollector extends Configuration {
  def apply()(implicit system: ActorSystem): Unit = {

    implicit val executionContext: ExecutionContext = system.dispatcher

    lazy val configuration = ConfigFactory.load()

    val metricConfiguration = parseConfiguration(configuration)

    val metrics = MetricCollector.generateMetricDefinitions(metricConfiguration.metrics).map { case (metricName, metricType) =>
      val kamonMetric = metricType.registerMetric(metricName)
      (metricName, SupportedKamonMetric(kamonMetric))
    }.toList

    lazy val jmxMetricCollectorActor = system.actorOf(Props(new JmxMetricCollectorActor(metrics, metricConfiguration.metrics)))
    system.scheduler.schedule(metricConfiguration.initialDelay, metricConfiguration.checkInterval, jmxMetricCollectorActor, CollectMetrics)
  }
}
