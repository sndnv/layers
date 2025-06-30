package io.github.sndnv.layers.telemetry.mocks

import scala.reflect.ClassTag

import io.github.sndnv.layers.telemetry.DefaultTelemetryContext
import io.github.sndnv.layers.telemetry.TelemetryContext
import io.github.sndnv.layers.telemetry.analytics.AnalyticsCollector
import io.github.sndnv.layers.telemetry.metrics.MetricsProvider

class MockTelemetryContext(collector: Option[AnalyticsCollector]) extends TelemetryContext {
  protected def providers(): Set[MetricsProvider] = Set(
    layers.api.endpoint,
    layers.persistence.keyValue,
    layers.security.authenticator,
    layers.security.keyProvider,
    layers.security.oauthClient
  )

  private lazy val underlying = new DefaultTelemetryContext(
    metricsProviders = providers(),
    analyticsCollector = collector.getOrElse(AnalyticsCollector.NoOp)
  )

  object layers {
    object api {
      val endpoint: MockApiMetrics.Endpoint = MockApiMetrics.Endpoint()
    }

    object persistence {
      val keyValue: MockPersistenceMetrics.KeyValueStore = MockPersistenceMetrics.KeyValueStore()
    }

    object security {
      val authenticator: MockSecurityMetrics.Authenticator = MockSecurityMetrics.Authenticator()
      val keyProvider: MockSecurityMetrics.KeyProvider = MockSecurityMetrics.KeyProvider()
      val oauthClient: MockSecurityMetrics.OAuthClient = MockSecurityMetrics.OAuthClient()
    }
  }

  override def metrics[M <: MetricsProvider](implicit tag: ClassTag[M]): M =
    underlying.metrics[M](tag)

  override def analytics: AnalyticsCollector =
    underlying.analytics
}

object MockTelemetryContext {
  def apply(): MockTelemetryContext =
    new MockTelemetryContext(collector = None)

  def apply(collector: AnalyticsCollector): MockTelemetryContext =
    new MockTelemetryContext(collector = Some(collector))
}
