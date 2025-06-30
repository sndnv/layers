package io.github.sndnv.layers.telemetry.mocks

import io.github.sndnv.layers.telemetry.analytics.AnalyticsCollector
import io.github.sndnv.layers.testing.UnitSpec

class MockTelemetryContextSpec extends UnitSpec {
  "A MockTelemetryContext" should "provide metrics" in {
    val context = MockTelemetryContext()

    noException should be thrownBy context.metrics[MockApiMetrics.Endpoint]
    noException should be thrownBy context.metrics[MockPersistenceMetrics.KeyValueStore]
    noException should be thrownBy context.metrics[MockSecurityMetrics.Authenticator]
    noException should be thrownBy context.metrics[MockSecurityMetrics.KeyProvider]
    noException should be thrownBy context.metrics[MockSecurityMetrics.OAuthClient]
  }

  it should "provide an analytics collector" in {
    MockTelemetryContext().analytics should be(an[AnalyticsCollector.NoOp.type])
    MockTelemetryContext(collector = new MockAnalyticsCollector()).analytics should be(a[MockAnalyticsCollector])
  }
}
