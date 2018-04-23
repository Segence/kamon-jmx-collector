package kamon.jmx.collector

import java.util.Optional

import com.segence.commons.jmx.collector.JmxCollector
import javax.management.ObjectName
import javax.management.openmbean.CompositeData
import kamon.jmx.collector.SupportedKamonMetricTypes.SupportedKamonMetricType

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

private[collector] object MetricCollector {
  private[collector] def collectMetrics(configuration: List[JmxMetricConfiguration]): (List[(String, String, Any)], List[Throwable]) = {

    val (successfulConfigWithObjectNames, errorsConfigWithObjectNames) = configuration.map { metricConfig =>
      Try {
        val jmxObjectName = new ObjectName(metricConfig.jmxMbeanQuery)
        (metricConfig.metricName, jmxObjectName, metricConfig.attributes)
      }
    }.partition(_.isSuccess)

    val configWithObjectNames = successfulConfigWithObjectNames.flatMap {
      case Success(configWithObjectName) => Some(configWithObjectName)
      case _ => None
    }

    val jmxMbeansAndAttributes = configWithObjectNames.map { case (_, jmxObjectName, attributes) =>
      (jmxObjectName, attributes.map(_.attributeName).toSet.asJava)
    }.toMap.asJava

    val jmxMbeansAndMetricNames = configWithObjectNames.map { case (metricName, jmxObjectName, _) =>
      (jmxObjectName, metricName)
    }.toMap

    val metricNamesAndValues = JmxCollector.queryAsSet(jmxMbeansAndAttributes).asScala.toList.map { mbeanMetricResult =>
      val maybeMBeanMetric = asScalaOption(mbeanMetricResult.getMBeanMetric)
      val maybeError = asScalaOption(mbeanMetricResult.getError)

      (maybeMBeanMetric, maybeError) match {
        case (Some(validMbeanMetric), None) =>
          Try { for {
              attribute <- validMbeanMetric.getAttributes.asScala.toList
              metricName <- jmxMbeansAndMetricNames.get(validMbeanMetric.getObjectInstance.getObjectName)
            } yield (metricName, attribute.getName, attribute.getValue)
          }
        case (_, Some(error)) =>
          Failure(error)
        case _ =>
          Failure(new RuntimeException("Invalid state: both MBean Metric and error defined"))
      }
    }

    val (successfulMetricNamesAndValues, errorsMetricNamesAndValues) = metricNamesAndValues.partition(_.isSuccess)

    val errorsFromConfigWithObjectNames = errorsConfigWithObjectNames.flatMap {
      case Failure(error) => Some(error)
      case _ => None
    }
    val errorsFromMetricNamesAndValues = errorsMetricNamesAndValues.flatMap {
      case Failure(error) => Some(error)
      case _ => None
    }

    val validMetrics = successfulMetricNamesAndValues.flatMap {
      case Success(validValue) => Some(validValue)
      case _ => None
    }.flatten

    (validMetrics, errorsFromConfigWithObjectNames ++ errorsFromMetricNamesAndValues)
  }

  private def asScalaOption[T](optional: Optional[T]) = if (!optional.isPresent) {
    None
  } else {
    Option(optional.orElseGet(null))
  }

  private[collector] def generateMetricName(metricName: String, attributeName: String, attributeKeyName: Option[String] = None)=
    s"jmx-$metricName-$attributeName${attributeKeyName.fold("")(keyName => s"-$keyName")}"

  private[collector] val extractAttributeValue: PartialFunction[Any, Try[Long]] = {
    case x: Long      => Try(x)
    case n: Number    => Try(n.asInstanceOf[Number].longValue)
    case invalidValue => Failure(new IllegalArgumentException(s"$invalidValue is not a valid number."))
  }

  @tailrec
  private def extractMetricsAndErrors(metrics: List[(String, String, List[String], Any)], results: (Map[String, Long], List[Throwable])): (Map[String, Long], List[Throwable]) =
    metrics match {
      case Nil =>
        results
      case (metricName, attributeName, attributeKeys, rawAttributeValue: CompositeData) :: rest =>

        val attributeKeyMetrics = attributeKeys.map { attributeKey =>
          (attributeKey, extractAttributeValue(rawAttributeValue.get(attributeKey)))
        }.map {
          case (attributeKey, Success(attributeValue)) =>
            val fullMetricName = generateMetricName(metricName, attributeName, Some(attributeKey))
            ( Some((fullMetricName, attributeValue)), None )
          case (_, Failure(error)) =>
            ( None, Some(error))
        }

        extractMetricsAndErrors(
          metrics.tail,
          (
           results._1 ++ attributeKeyMetrics.flatMap(_._1),
           results._2 ++ attributeKeyMetrics.flatMap(_._2)
          )
        )

      case (metricName, attributeName, _, rawAttributeValue) :: rest =>
        extractAttributeValue(rawAttributeValue) match {
          case Success(validAttributeValue) =>
            val fullMetricName = generateMetricName(metricName, attributeName)
            extractMetricsAndErrors(metrics.tail, (results._1 + (fullMetricName -> validAttributeValue), results._2))
          case Failure(error) =>
            extractMetricsAndErrors(metrics.tail, (results._1, error :: results._2))
        }
    }

  private[collector] def generateMetricDefinitions(configuration: List[JmxMetricConfiguration]): Map[String, SupportedKamonMetricType] = {
    for {
      metricConfiguration <- configuration
      attribute <- metricConfiguration.attributes
    } yield attribute.keys match {
      case Nil => (generateMetricName(metricConfiguration.metricName, attribute.attributeName), attribute.metricType) :: Nil
      case validAttributeKeys => for {
             attributeKey <- validAttributeKeys
           } yield (generateMetricName(metricConfiguration.metricName, attribute.attributeName, Option(attributeKey)), attribute.metricType)
    }
  }.flatten.toMap

  private[collector] def generateMetrics(configuration: List[JmxMetricConfiguration]): (Map[String, Long], List[Throwable]) = {
    val (results, errors) = collectMetrics(configuration)

    val resultsIncludingAttributeKeys = for {
      (metricName, attributeName, attributeValue) <- results
      configurationEntry <- configuration
      configurationAttribute <- configurationEntry.attributes
      if metricName == configurationEntry.metricName && attributeName == configurationAttribute.attributeName
    } yield (metricName, attributeName, configurationAttribute.keys, attributeValue)

    val (validMetricResults, errorsInMetricResults) = extractMetricsAndErrors(resultsIncludingAttributeKeys, (Map.empty, Nil))
    (validMetricResults, errors ++ errorsInMetricResults)
  }
}
