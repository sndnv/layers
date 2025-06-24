package io.github.sndnv.layers.telemetry.analytics

import org.apache.pekko.Done

import java.time.Instant
import scala.concurrent.Future

trait AnalyticsPersistence {
  type Self

  def cache(entry: AnalyticsEntry): Unit
  def transmit(entry: AnalyticsEntry): Future[Done]
  def restore(): Future[Option[AnalyticsEntry]]

  def lastCached: Instant
  def lastTransmitted: Instant

  def withClientProvider(provider: AnalyticsClient.Provider): Self
}
