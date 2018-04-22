package kamon.jmx.collector

import com.typesafe.config.{Config, ConfigValueType}
import kamon.jmx.collector.SupportedKamonMetricTypes.SupportedKamonMetricType

import scala.collection.JavaConverters._
import scala.concurrent.duration.{Duration, FiniteDuration}

case class JmxMetricAttribute(attributeName: String, metricType: SupportedKamonMetricType[_])
case class JmxMetricConfiguration(metricName: String, jmxMbeanQuery: String, attributes: List[JmxMetricAttribute])
case class JmxCollectorConfiguration(initialDelay: FiniteDuration, checkInterval: FiniteDuration, metrics: List[JmxMetricConfiguration])

private[collector] trait Configuration {
  def parseConfiguration(config: Config): JmxCollectorConfiguration = {

    val kamonJmxCollectorConfig = config.getConfig("kamon.jmx-collector")

    val initialDelay: FiniteDuration = Duration.fromNanos( kamonJmxCollectorConfig.getDuration("initial-delay").toNanos )
    val checkInterval: FiniteDuration = Duration.fromNanos( kamonJmxCollectorConfig.getDuration("value-check-interval-ms").toNanos )

    val mbeans = kamonJmxCollectorConfig.getObjectList("mbeans").asScala.toList.map { confObj =>
      val nameObj = confObj.get("metric-name")
      val queryObj = confObj.get("jmxQuery")
      val attrListObj = confObj.get("attributes")

      require(nameObj.valueType() == ConfigValueType.STRING, "name must be a string")
      require(queryObj.valueType() == ConfigValueType.STRING, "jmxQuery must be a string")
      require(attrListObj.valueType() == ConfigValueType.LIST, "mbeans must be an array")

      val metricName: String = nameObj.unwrapped().asInstanceOf[String]
      val jmxQuery: String = queryObj.unwrapped().asInstanceOf[String]

      val attributesList: Seq[(String, String)] = for {
        attributeDefinition <- attrListObj.unwrapped().asInstanceOf[java.util.List[Any]].asScala
        (attributeName, attributeValue) <- attributeDefinition.asInstanceOf[java.util.HashMap[String, Any]].asScala.toMap
      } yield (attributeName, s"$attributeValue")

      val attributeDefinitions = attributesList.toList.grouped(2).map {
        case ( ("name", attributeName) :: ("type", metricType) :: Nil) =>
          JmxMetricAttribute(attributeName, SupportedKamonMetricTypes.parse(metricType))
        case invalidAttributeDefinition =>
          throw new IllegalArgumentException(s"Expected a name and type pair but got[$invalidAttributeDefinition]")
      }.toList

      JmxMetricConfiguration(metricName, jmxQuery, attributeDefinitions)
    }

    JmxCollectorConfiguration(initialDelay, checkInterval, mbeans)
  }
}
