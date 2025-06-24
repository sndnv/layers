package io.github.sndnv.layers.telemetry.analytics

import org.apache.pekko.Done

import scala.concurrent.Future

trait AnalyticsClient {
  def sendAnalyticsEntry(entry: AnalyticsEntry): Future[Done]
}

object AnalyticsClient {
  trait Provider {
    def client: AnalyticsClient
  }

  object Provider {
    def apply(f: () => AnalyticsClient): Provider = new Provider {
      override def client: AnalyticsClient = f()
    }
  }
}
