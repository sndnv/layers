package io.github.sndnv.layers.security

import io.github.sndnv.layers.telemetry.mocks.MockMeter
import io.github.sndnv.layers.testing.UnitSpec

class MetricsSpec extends UnitSpec {
  "Metrics" should "provide a no-op implementation" in {
    Metrics.noop() should be(Set(Metrics.Authenticator.NoOp, Metrics.KeyProvider.NoOp, Metrics.OAuthClient.NoOp))

    val authenticatorMetrics = Metrics.Authenticator.NoOp
    noException should be thrownBy authenticatorMetrics.recordAuthentication(authenticator = null, successful = false)

    val keyProviderMetrics = Metrics.KeyProvider.NoOp
    noException should be thrownBy keyProviderMetrics.recordKeyRefresh(provider = null, successful = false)
    noException should be thrownBy keyProviderMetrics.recordKeyRefresh(provider = null, successful = true)

    val oauthClientMetrics = Metrics.OAuthClient.NoOp
    noException should be thrownBy oauthClientMetrics.recordToken(endpoint = null, grantType = null)
  }

  they should "provide a default implementation" in {
    val meter = MockMeter()

    val default = Metrics.default(meter = meter, namespace = "test")

    val authenticatorMetrics = default.collectFirst { case metrics: Metrics.Authenticator.Default => metrics } match {
      case Some(m) => m
      case None    => fail("Expected Metrics.Authenticator.Default but none were found")
    }

    authenticatorMetrics.recordAuthentication(authenticator = "test", successful = false)
    authenticatorMetrics.recordAuthentication(authenticator = "test", successful = true)

    val keyProviderMetrics = default.collectFirst { case metrics: Metrics.KeyProvider.Default => metrics } match {
      case Some(m) => m
      case None    => fail("Expected Metrics.KeyProvider.Default but none were found")
    }
    keyProviderMetrics.recordKeyRefresh(provider = "test", successful = false)
    keyProviderMetrics.recordKeyRefresh(provider = "test", successful = true)

    val oauthClientMetrics = default.collectFirst { case metrics: Metrics.OAuthClient.Default => metrics } match {
      case Some(m) => m
      case None    => fail("Expected Metrics.OAuthClient.Default but none were found")
    }

    oauthClientMetrics.recordToken(endpoint = "test", grantType = "client_credentials")
    oauthClientMetrics.recordToken(endpoint = "test", grantType = "client_credentials")
    oauthClientMetrics.recordToken(endpoint = "test", grantType = "client_credentials")

    meter.metric(name = "test_authenticators_authentication_successful") should be(1)
    meter.metric(name = "test_authenticators_authentication_failed") should be(1)
    meter.metric(name = "test_key_providers_key_refresh_successful") should be(1)
    meter.metric(name = "test_key_providers_key_refresh_failed") should be(1)
    meter.metric(name = "test_oauth_clients_tokens") should be(3)
  }
}
