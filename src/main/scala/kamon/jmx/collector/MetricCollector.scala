package kamon.jmx.collector

import java.{util => javautil}

import com.segence.commons.jmx.collector.{JmxCollector, MBeanMetricResult}
import javax.management.ObjectName
import javax.management.openmbean.CompositeData
import kamon.jmx.collector.SupportedKamonMetricTypes.SupportedKamonMetricType

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

private[collector] object MetricCollector {

  case class FullMetricName(metricName: String, metricNameSuffix: Option[String] = None) {
    override def toString: String = s"$metricName${metricNameSuffix.fold("") { suffix => s"-$suffix" }}"
  }

  def getJmxMbeanEntities(configuration: List[JmxMetricConfiguration]):
    (Map[ObjectName, Set[String]], Map[ObjectName, String], List[(String, ObjectName, List[JmxMetricAttribute])], List[Throwable]) = {

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

    val (jmxMbeansAndAttributes, jmxMbeansAndMetricNames) = configWithObjectNames.map { case (metricName, jmxObjectName, attributes) =>
      (jmxObjectName -> attributes.map(_.attributeName).toSet, jmxObjectName -> metricName)
    }.unzip

    val errorsFromConfigWithObjectNames = errorsConfigWithObjectNames.flatMap {
      case Failure(error) => Some(error)
      case _ => None
    }

    (jmxMbeansAndAttributes.toMap, jmxMbeansAndMetricNames.toMap, configWithObjectNames, errorsFromConfigWithObjectNames)
  }

  def getMetricName(jmxObjectName: ObjectName, queryMbeansAndMetricNames: Map[ObjectName, String]): Option[FullMetricName] =
    queryMbeansAndMetricNames.get(jmxObjectName) match {
      case Some(validResult) =>
        Option(FullMetricName(validResult))
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

        FullMetricName(metricName, Option(metricNameSuffix))
      }
    }

  def collectMetrics(jmxMbeansAndAttributes: Map[ObjectName, Set[String]],
                     jmxMbeansAndMetricNames: Map[ObjectName, String],
                     configWithObjectNames: List[(String, ObjectName, List[JmxMetricAttribute])],
                     errorsFromConfigWithObjectNames: List[Throwable],
                     queryJmxBeansFn: (javautil.Map[ObjectName, javautil.Set[String]] => javautil.Set[MBeanMetricResult])
                    ): (List[(FullMetricName, String, Any, SupportedKamonMetricType)], List[Throwable]) = {

    val metricNamesAndValues =
      queryJmxBeansFn(jmxMbeansAndAttributes.map { case (objectName, attributes) =>
        objectName -> attributes.asJava
      }.asJava).asScala.toList.map { mbeanMetricResult =>
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
                if resolvedMetricName.metricName == metricName && attributeConfig.attributeName == attribute.getName
              } yield (resolvedMetricName, attribute.getName, attribute.getValue, attributeConfig.metricType)
            }
          case (_, Some(error)) =>
            Failure(error)
          case _ =>
            Failure(new RuntimeException("Invalid state: both MBean Metric and error defined"))
        }
    }

    val (successfulMetricNamesAndValues, errorsMetricNamesAndValues) = metricNamesAndValues.partition(_.isSuccess)


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

  private def asScalaOption[T](optional: javautil.Optional[T]): Option[T] = if (!optional.isPresent) {
    None
  } else {
    Option(optional.orElseGet(null))
  }

  def generateMetricName(metricName: String, attributeName: String, attributeKeyName: Option[String] = None)=
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
      case (metricName, attributeName, attributeKeys, rawAttributeValue: CompositeData, metricType) :: _ =>

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

      case (metricName, attributeName, _, rawAttributeValue, metricType) :: _ =>
        extractAttributeValue(rawAttributeValue) match {
          case Success(validAttributeValue) =>
            val fullMetricName = generateMetricName(metricName, attributeName)
            extractMetricsAndErrors(metrics.tail, ((fullMetricName, validAttributeValue, metricType) :: results._1, results._2))
          case Failure(error) =>
            extractMetricsAndErrors(metrics.tail, (results._1, error :: results._2))
        }
    }

  def generateMetricDefinitions(configuration: List[JmxMetricConfiguration]): Map[String, SupportedKamonMetricType] = {
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

  def generateMetrics(configuration: List[JmxMetricConfiguration],
                      jmxMbeansAndAttributes: Map[ObjectName, Set[String]],
                      jmxMbeansAndMetricNames: Map[ObjectName, String],
                      configWithObjectNames: List[(String, ObjectName, List[JmxMetricAttribute])],
                      errorsFromConfigWithObjectNames: List[Throwable]): (List[(String, Long, SupportedKamonMetricType)], List[Throwable]) = {
    val (results, errors) =
      collectMetrics(jmxMbeansAndAttributes, jmxMbeansAndMetricNames, configWithObjectNames, errorsFromConfigWithObjectNames, JmxCollector.queryAsSet)

    val resultsIncludingAttributeKeys = for {
      (metricName, attributeName, attributeValue, metricType) <- results
      configurationEntry <- configuration
      configurationAttribute <- configurationEntry.attributes
      if metricName.metricName == configurationEntry.metricName && attributeName == configurationAttribute.attributeName
    } yield (s"$metricName", attributeName, configurationAttribute.keys, attributeValue, metricType)

    val (validMetricResults, errorsInMetricResults) = extractMetricsAndErrors(resultsIncludingAttributeKeys, (Nil, Nil))
    (validMetricResults, errors ++ errorsInMetricResults)
  }
}
