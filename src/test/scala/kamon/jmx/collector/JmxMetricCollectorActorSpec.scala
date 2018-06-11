package kamon.jmx.collector

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import kamon.Tags
import kamon.jmx.collector.JmxMetricCollectorActor.CollectMetrics
import kamon.jmx.collector.SupportedKamonMetricTypes.{Counter, SupportedKamonMetricType}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike}
import org.scalatest.mockito.MockitoSugar
import org.mockito.Mockito.{doAnswer, times, verify, when}
import org.mockito.internal.stubbing.answers.{AnswersWithDelay, Returns}

import scala.concurrent.duration._

class JmxMetricCollectorActorSpec extends TestKit(ActorSystem("JmxMetricCollectorActorSpec")) with ImplicitSender with FlatSpecLike with BeforeAndAfterAll with Eventually {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "A JMX metric collector actor" should "not collect metrics when an earlier metric collection task is still running" in new JmxMetricCollectorActorFixture {

    doAnswer(new AnswersWithDelay(TestTaskExecutionTime, new Returns(SuccessfulMetricCollectionResult))).when(collectMetricsMock).apply()

    underTest ! CollectMetrics
    underTest ! CollectMetrics

    eventually(timeout(4 seconds), interval(5 millis)) {
      verify(metricTypeMock).record(TestMetricName, TestMetricValue, TestMetricTags)
      verify(collectMetricsMock).apply()
    }
  }

  it should "collect metrics when an earlier metric collection task has already finished" in new JmxMetricCollectorActorFixture {

    when(collectMetricsMock.apply()).thenReturn(SuccessfulMetricCollectionResult)

    underTest ! CollectMetrics
    Thread.sleep(1000L)
    underTest ! CollectMetrics

    eventually(timeout(4 seconds), interval(5 millis)) {
      verify(metricTypeMock, times(2)).record(TestMetricName, TestMetricValue, TestMetricTags)
      verify(collectMetricsMock, times(2)).apply()
    }
  }

  trait JmxMetricCollectorActorFixture extends ScalaFutures with MockitoSugar {

    implicit val timeout = Timeout(5 seconds)

    val metricTypeMock = mock[SupportedKamonMetricType]
    val collectMetricsMock = mock[() => (List[(String, Long, Tags, SupportedKamonMetricType)], List[Throwable])]

    val TestMetricName = "testMetricName"
    val TestMetricValue = 10L
    val TestMetricTags = Map("someTag" -> "someValue")
    val TestTaskExecutionTime = (2 seconds).toMillis

    val SuccessfulMetricCollectionResult: (List[(String, Long, Tags, SupportedKamonMetricType)], List[Throwable]) =
      ( (TestMetricName, TestMetricValue, TestMetricTags, metricTypeMock) :: Nil, Nil)

    val underTest = system.actorOf(Props(new JmxMetricCollectorActor(collectMetricsMock)))
  }
}
