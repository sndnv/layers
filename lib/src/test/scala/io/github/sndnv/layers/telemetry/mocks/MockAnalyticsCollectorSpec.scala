package io.github.sndnv.layers.telemetry.mocks

import scala.concurrent.duration._

import io.github.sndnv.layers.testing.UnitSpec
import org.scalatest.concurrent.Eventually

class MockAnalyticsCollectorSpec extends UnitSpec with Eventually {
  "A MockAnalyticsCollector" should "record events and failures" in {
    val collector = new MockAnalyticsCollector()

    val initial = collector.state.await
    initial.events should be(empty)
    initial.failures should be(empty)

    collector.recordEvent(name = "test", attributes = Map.empty[String, String])
    collector.recordFailure(message = "test")

    val updated = collector.state.await
    updated.events should not be empty
    updated.failures should not be empty
  }

  it should "transmit entries" in {
    val collector = new MockAnalyticsCollector()

    val persistence = collector.persistence match {
      case Some(persistence: MockAnalyticsPersistence) => persistence
      case other                                       => fail(s"Expected mock analytics persistence but [$other] found")
    }

    persistence.transmitted should be(empty)

    collector.send()

    eventually {
      persistence.transmitted should not be empty
    }
  }

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 100.milliseconds)
}
