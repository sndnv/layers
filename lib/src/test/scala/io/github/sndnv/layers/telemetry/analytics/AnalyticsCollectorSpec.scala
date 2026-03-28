package io.github.sndnv.layers.telemetry.analytics

import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.Future

import io.github.sndnv.layers.testing.UnitSpec

class AnalyticsCollectorSpec extends UnitSpec {
  "An AnalyticsCollector" should "record failure stack traces" in {
    val recordedStackTrace = new AtomicReference[Option[String]](None)
    val collector = new AnalyticsCollector {
      override def recordEvent(name: String, attributes: Map[String, String]): Unit = ()
      override def recordFailure(message: String, stackTrace: Option[String]): Unit = recordedStackTrace.set(stackTrace)
      override def state: Future[AnalyticsEntry] = Future.failed(new RuntimeException("Test failure"))
      override def send(): Unit = ()
      override def persistence: Option[AnalyticsPersistence] = None
    }

    collector.recordFailure(new RuntimeException("Recorded failure"))

    val trace = recordedStackTrace.get() match {
      case Some(value) => value
      case None        => fail("Expected a stack trace but none was found")
    }

    trace should include("java.lang.RuntimeException: Recorded failure")
    trace should include("at io.github.sndnv.layers.telemetry.analytics.AnalyticsCollectorSpec")
    trace should include("at org.scalatest.flatspec.AsyncFlatSpecLike")
  }

  "A NoOp AnalyticsCollector" should "record nothing" in {
    val collector = AnalyticsCollector.NoOp

    collector.recordEvent("test_event")
    collector.recordEvent("test_event", "a" -> "b")
    collector.recordEvent("test_event", "a" -> "b", "c" -> "d")
    collector.recordEvent("test_event", Map("a" -> "b"))

    collector.recordFailure(e = new RuntimeException("Test failure"))
    collector.recordFailure(message = "Other failure")
    collector.recordFailure(message = "Other failure", stackTrace = Some("xyz"))

    collector.persistence should be(empty)

    for {
      state <- collector.state
    } yield {
      noException should be thrownBy collector.send()
      state.events should be(empty)
      state.failures should be(empty)
    }
  }
}
