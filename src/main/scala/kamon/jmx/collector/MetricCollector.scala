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

  private[collector] def getMetricName(jmxObjectName: ObjectName, queryMbeansAndMetricNames: Map[ObjectName, String]): Option[String] =
    queryMbeansAndMetricNames.get(jmxObjectName) match {
      case validResult @ Some(_) => validResult
      case _ => for {
        (objectName, metricName) <- queryMbeansAndMetricNames.find { case (objectName, _) => objectName.getDomain == jmxObjectName.getDomain }
        jmxMbeanProperties = objectName.getKeyPropertyList.entrySet.asScala
        queryMbeanProperties = jmxObjectName.getKeyPropertyList.entrySet.asScala
        zippedProperties = jmxMbeanProperties.zip(queryMbeanProperties)
        areAllPropertiesMatching = zippedProperties.forall { case (jmxProperty, queryProperty) =>
          jmxProperty.getKey == queryProperty.getKey && (jmxProperty.getValue == "*" || jmxProperty.getValue == queryProperty.getValue)
        }
        if areAllPropertiesMatching
      } yield {
        val metricNameSuffix = zippedProperties.filter { case (jmxProperty, _) =>
                                    jmxProperty.getValue == "*"
                                }.map { case (_, queryProperty) =>
                                  queryProperty.getValue
                                }.mkString("-")

        s"$metricName-$metricNameSuffix"
      }
    }

  private[collector] def collectMetrics(configuration: List[JmxMetricConfiguration]): (List[(String, String, Any, SupportedKamonMetricType)], List[Throwable]) = {

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
          Try {
            for {
              (metricName, _, attributes) <- configWithObjectNames
              attributeConfig <- attributes
              attribute <- validMbeanMetric.getAttributes.asScala.toList
              resolvedMetricName <- getMetricName(validMbeanMetric.getObjectInstance.getObjectName, jmxMbeansAndMetricNames)
              if resolvedMetricName.startsWith(metricName) && attributeConfig.attributeName == attribute.getName
            } yield (resolvedMetricName, attribute.getName, attribute.getValue, attributeConfig.metricType)
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
  private def extractMetricsAndErrors(metrics: List[(String, String, List[String], Any, SupportedKamonMetricType)], results: (List[(String, Long, SupportedKamonMetricType)], List[Throwable])): (List[(String, Long, SupportedKamonMetricType)], List[Throwable]) =
    metrics match {
      case Nil =>
        results
      case (metricName, attributeName, attributeKeys, rawAttributeValue: CompositeData, metricType) :: rest =>

        val attributeKeyMetrics = attributeKeys.map { attributeKey =>
          (attributeKey, extractAttributeValue(rawAttributeValue.get(attributeKey)), metricType)
        }.map {
          case (attributeKey, Success(attributeValue), attributeMetricType) =>
            val fullMetricName = generateMetricName(metricName, attributeName, Some(attributeKey))
            ( Some((fullMetricName, attributeValue, attributeMetricType)), None )
          case (_, Failure(error), _) =>
            ( None, Some(error))
        }

        extractMetricsAndErrors(
          metrics.tail,
          (
           results._1 ++ attributeKeyMetrics.flatMap(_._1),
           results._2 ++ attributeKeyMetrics.flatMap(_._2)
          )
        )

      case (metricName, attributeName, _, rawAttributeValue, metricType) :: rest =>
        extractAttributeValue(rawAttributeValue) match {
          case Success(validAttributeValue) =>
            val fullMetricName = generateMetricName(metricName, attributeName)
            extractMetricsAndErrors(metrics.tail, ((fullMetricName, validAttributeValue, metricType) :: results._1, results._2))
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

  private[collector] def generateMetricDefinitions2(configuration: List[(String, Long, SupportedKamonMetricType)]): Map[String, SupportedKamonMetricType] = {
    for {
      metricConfiguration <- configuration

    } yield (metricConfiguration._1, metricConfiguration._3)
  }.toMap

  private[collector] def generateMetrics(configuration: List[JmxMetricConfiguration]): (List[(String, Long, SupportedKamonMetricType)], List[Throwable]) = {
    val (results, errors) = collectMetrics(configuration)

    val resultsIncludingAttributeKeys = for {
      (metricName, attributeName, attributeValue, metricType) <- results
      configurationEntry <- configuration
      configurationAttribute <- configurationEntry.attributes
      // TODO return a pair of metric name prefix and the rest and compare against the left side instead of using `startsWith`
      if metricName.startsWith(configurationEntry.metricName) && attributeName == configurationAttribute.attributeName
    } yield (metricName, attributeName, configurationAttribute.keys, attributeValue, metricType)

    val (validMetricResults, errorsInMetricResults) = extractMetricsAndErrors(resultsIncludingAttributeKeys, (Nil, Nil))
    (validMetricResults, errors ++ errorsInMetricResults)
  }
}
