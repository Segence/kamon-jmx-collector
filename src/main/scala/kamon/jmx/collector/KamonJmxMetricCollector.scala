package kamon.jmx.collector

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory
import kamon.jmx.collector.JmxMetricCollectorActor.CollectMetrics

import scala.concurrent.ExecutionContext

object KamonJmxMetricCollector extends Configuration {
  def apply()(implicit system: ActorSystem): Unit = {

    implicit val executionContext: ExecutionContext = system.dispatcher

    lazy val configuration = parseConfiguration(ConfigFactory.load())
    lazy val jmxMetricCollectorActor = system.actorOf(Props(new JmxMetricCollectorActor(configuration.metrics)))
    system.scheduler.schedule(configuration.initialDelay, configuration.checkInterval, jmxMetricCollectorActor, CollectMetrics)
  }
}
