package io.github.sndnv.layers.persistence

import scala.concurrent.Future

import io.github.sndnv.layers.telemetry.mocks.MockMeter
import io.github.sndnv.layers.testing.UnitSpec

class MetricsSpec extends UnitSpec {
  "Metrics" should "provide a no-op implementation" in {
    Metrics.noop() should be(
      Set(
        Metrics.Store.NoOp
      )
    )

    val future = Future.successful(0)

    val keyValueMetrics = Metrics.Store.NoOp
    noException should be thrownBy keyValueMetrics.recordPut(store = null)(future)
    noException should be thrownBy keyValueMetrics.recordGet(store = null)(future)
    noException should be thrownBy keyValueMetrics.recordDelete(store = null)(future)
    noException should be thrownBy keyValueMetrics.recordContains(store = null)(future)
    noException should be thrownBy keyValueMetrics.recordList(store = null)(future)
  }

  they should "provide a default implementation" in {
    val meter = MockMeter()

    val future = Future.successful(0)

    val keyValueMetrics = Metrics
      .default(meter = meter, namespace = "test")
      .collectFirst { case metrics: Metrics.Store.Default =>
        metrics
      } match {
      case Some(m) => m
      case None    => fail("Expected Metrics.Store.Default but none were found")
    }

    keyValueMetrics.recordPut(store = "test")(future)
    keyValueMetrics.recordPut(store = "test")(future)
    keyValueMetrics.recordPut(store = "test")(future)
    keyValueMetrics.recordGet(store = "test")(future)
    keyValueMetrics.recordDelete(store = "test")(future)
    keyValueMetrics.recordDelete(store = "test")(future)
    keyValueMetrics.recordContains(store = "test")(future)
    keyValueMetrics.recordList(store = "test")(future)

    meter.metric(name = "test_persistence_store_operation_duration") should be(8)
  }
}
