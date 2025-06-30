package io.github.sndnv.layers.telemetry.mocks

import java.time.Instant

import io.github.sndnv.layers.telemetry.ApplicationInformation
import io.github.sndnv.layers.telemetry.analytics.AnalyticsEntry
import io.github.sndnv.layers.testing.UnitSpec

class MockAnalyticsPersistenceSpec extends UnitSpec {
  "A MockAnalyticsPersistence" should "cache and transmit entries" in {
    val persistence = MockAnalyticsPersistence()

    persistence.lastCached should be(Instant.EPOCH)
    persistence.cached should be(empty)

    persistence.lastTransmitted should be(Instant.EPOCH)
    persistence.transmitted should be(empty)

    persistence.cache(entry = AnalyticsEntry.collected(ApplicationInformation.none))
    persistence.transmit(entry = AnalyticsEntry.collected(ApplicationInformation.none))

    persistence.lastCached should not be Instant.EPOCH
    persistence.cached should not be empty

    persistence.lastTransmitted should not be Instant.EPOCH
    persistence.transmitted should not be empty
  }

  it should "restore entries" in {
    MockAnalyticsPersistence()
      .restore()
      .await should be(empty)

    MockAnalyticsPersistence(existing = AnalyticsEntry.collected(ApplicationInformation.none))
      .restore()
      .await should not be empty

    MockAnalyticsPersistence(existing = new RuntimeException("Test failure"))
      .restore()
      .failed
      .await
      .getMessage should be("Test failure")
  }

  it should "not support setting client providers" in {
    val persistence = MockAnalyticsPersistence()

    persistence.withClientProvider(provider = null) should be(persistence)
  }
}
