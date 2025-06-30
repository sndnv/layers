package io.github.sndnv.layers.telemetry.mocks

import io.github.sndnv.layers.testing.UnitSpec
import org.apache.pekko.http.scaladsl.model.HttpRequest
import org.apache.pekko.http.scaladsl.model.HttpResponse

class MockApiMetricsSpec extends UnitSpec {
  "MockApiMetrics" should "support endpoint metrics recording" in {
    val metrics = MockApiMetrics.Endpoint()

    metrics.request should be(0)
    metrics.response should be(0)

    metrics.recordRequest(request = HttpRequest())
    metrics.recordResponse(requestStart = 1, request = HttpRequest(), response = HttpResponse())

    metrics.request should be(1)
    metrics.response should be(1)
  }
}
