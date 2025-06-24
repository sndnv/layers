package io.github.sndnv.layers.telemetry

import io.github.sndnv.layers.UnitSpec
import io.github.sndnv.layers.api.{Metrics => ApiMetrics}
import io.github.sndnv.layers.persistence.{Metrics => PersistenceMetrics}
import io.github.sndnv.layers.telemetry.analytics.AnalyticsCollector
import io.github.sndnv.layers.telemetry.mocks.{MockApiMetrics, MockPersistenceMetrics}

class DefaultTelemetryContextSpec extends UnitSpec {
  "A DefaultTelemetryContext" should "provide metrics" in {
    val context = DefaultTelemetryContext(
      metricsProviders = Set(
        MockApiMetrics.Endpoint(),
        MockPersistenceMetrics.KeyValueStore()
      ),
      analyticsCollector = AnalyticsCollector.NoOp
    )

    noException should be thrownBy context.metrics[ApiMetrics.Endpoint]
    noException should be thrownBy context.metrics[PersistenceMetrics.Store]
  }

  it should "fail if a requested provider is not available" in {
    val context = DefaultTelemetryContext(
      metricsProviders = Set(
        MockApiMetrics.Endpoint()
      ),
      analyticsCollector = AnalyticsCollector.NoOp
    )

    noException should be thrownBy context.metrics[ApiMetrics.Endpoint]
    an[IllegalStateException] should be thrownBy context.metrics[PersistenceMetrics.Store]
  }

  it should "provide an analytics collector" in {
    val context = DefaultTelemetryContext(
      metricsProviders = Set(
        MockApiMetrics.Endpoint()
      ),
      analyticsCollector = AnalyticsCollector.NoOp
    )

    context.analytics should be(an[AnalyticsCollector.NoOp.type])
  }
}
