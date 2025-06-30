package io.github.sndnv.layers.telemetry.mocks

import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.Future

import io.github.sndnv.layers.telemetry.ApplicationInformation
import io.github.sndnv.layers.telemetry.analytics.AnalyticsCollector
import io.github.sndnv.layers.telemetry.analytics.AnalyticsEntry
import io.github.sndnv.layers.telemetry.analytics.AnalyticsPersistence

class MockAnalyticsCollector extends AnalyticsCollector {
  private val mockPersistence = MockAnalyticsPersistence()
  private val entryRef: AtomicReference[AnalyticsEntry.Collected] =
    new AtomicReference(AnalyticsEntry.collected(app = ApplicationInformation.none))

  override def recordEvent(name: String, attributes: Map[String, String]): Unit = {
    val _ = entryRef.updateAndGet((entry: AnalyticsEntry.Collected) => entry.withEvent(name, attributes))
  }

  override def recordFailure(message: String): Unit = {
    val _ = entryRef.updateAndGet((entry: AnalyticsEntry.Collected) => entry.withFailure(message))
  }

  override def state: Future[AnalyticsEntry] =
    Future.successful(entryRef.get())

  override def send(): Unit = {
    val _ = mockPersistence.transmit(entryRef.get())
  }

  override def persistence: Option[AnalyticsPersistence] =
    Some(mockPersistence)
}
