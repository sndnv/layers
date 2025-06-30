package io.github.sndnv.layers.telemetry.mocks

import io.github.sndnv.layers.testing.UnitSpec

class MockSecurityMetricsSpec extends UnitSpec {
  "MockSecurityMetrics" should "support authenticator metrics recording" in {
    val metrics = MockSecurityMetrics.Authenticator()

    metrics.authentication should be(0)

    metrics.recordAuthentication(authenticator = "test", successful = true)

    metrics.authentication should be(1)
  }

  they should "support key provider metrics recording" in {
    val metrics = MockSecurityMetrics.KeyProvider()

    metrics.keyRefresh should be(0)

    metrics.recordKeyRefresh(provider = "test ", successful = true)

    metrics.keyRefresh should be(1)
  }

  they should "support OAuth client metrics recording" in {
    val metrics = MockSecurityMetrics.OAuthClient()

    metrics.token should be(0)

    metrics.recordToken(endpoint = "test", grantType = "test")

    metrics.token should be(1)
  }
}
