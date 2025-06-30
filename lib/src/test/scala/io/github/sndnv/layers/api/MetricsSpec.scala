package io.github.sndnv.layers.api

import io.github.sndnv.layers.telemetry.mocks.MockMeter
import io.github.sndnv.layers.testing.UnitSpec
import org.apache.pekko.http.scaladsl.model.HttpRequest
import org.apache.pekko.http.scaladsl.model.HttpResponse

class MetricsSpec extends UnitSpec {
  "Metrics" should "provide a no-op implementation" in {
    Metrics.noop() should be(Set(Metrics.Endpoint.NoOp))

    val metrics = Metrics.Endpoint.NoOp

    noException should be thrownBy metrics.recordRequest(
      request = null
    )

    noException should be thrownBy metrics.recordResponse(
      requestStart = 0,
      request = null,
      response = null
    )
  }

  they should "provide a default implementation" in {
    val meter = MockMeter()
    val metrics = Metrics
      .default(meter = meter, namespace = "test")
      .collectFirst { case metrics: Metrics.Endpoint.Default =>
        metrics
      } match {
      case Some(m) => m
      case None    => fail("Expected Metrics.Endpoint.Default but none were found")
    }

    metrics.recordRequest(request = HttpRequest())
    metrics.recordRequest(request = HttpRequest())
    metrics.recordResponse(requestStart = 0, request = HttpRequest(), response = HttpResponse())

    meter.metric(name = "test_endpoints_requests") should be(2)
    meter.metric(name = "test_endpoints_response_times") should be(1)
  }
}
