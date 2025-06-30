package io.github.sndnv.layers.telemetry.mocks

import scala.concurrent.Future

import io.github.sndnv.layers.telemetry.ApplicationInformation
import io.github.sndnv.layers.telemetry.analytics.AnalyticsEntry
import io.github.sndnv.layers.testing.UnitSpec

class MockAnalyticsClientSpec extends UnitSpec {
  "A MockAnalyticsClient" should "track entry transmission" in {
    val client = MockAnalyticsClient()

    client.sent should be(0)
    client.lastEntry should be(empty)

    client.sendAnalyticsEntry(entry = AnalyticsEntry.collected(ApplicationInformation.none)).await

    client.sent should be(1)
    client.lastEntry should not be empty
  }

  it should "support providing transmission results" in {
    val client = MockAnalyticsClient(Future.failed(new RuntimeException("Test failure")))

    val e = client.sendAnalyticsEntry(entry = AnalyticsEntry.collected(ApplicationInformation.none)).failed.await

    e.getMessage should be("Test failure")
  }
}
