package io.github.sndnv.layers.telemetry.mocks

import scala.concurrent.Future

import io.github.sndnv.layers.persistence.Metrics.Store
import io.github.sndnv.layers.testing.UnitSpec
import org.apache.pekko.Done

class MockPersistenceMetricsSpec extends UnitSpec {
  "MockPersistenceMetrics" should "support key-value store metrics recording" in {
    val metrics = MockPersistenceMetrics.KeyValueStore()

    metrics.put should be(0)
    metrics.get should be(0)
    metrics.delete should be(0)
    metrics.contains should be(0)
    metrics.list should be(0)

    metrics.recordPut(store = "test") { Future.successful(Done) }
    metrics.recordGet(store = "test") { Future.successful(Done) }
    metrics.recordDelete(store = "test") { Future.successful(Done) }
    metrics.recordContains(store = "test") { Future.successful(Done) }
    metrics.recordList(store = "test") { Future.successful(Done) }

    metrics.put should be(1)
    metrics.get should be(1)
    metrics.delete should be(1)
    metrics.contains should be(1)
    metrics.list should be(1)

    // should do nothing
    metrics.recordOperation(store = "test", operation = Store.Operation.Contains) { Future.successful(Done) }

    metrics.put should be(1)
    metrics.get should be(1)
    metrics.delete should be(1)
    metrics.contains should be(1)
    metrics.list should be(1)
  }
}
