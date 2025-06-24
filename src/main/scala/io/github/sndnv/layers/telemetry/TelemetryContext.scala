package io.github.sndnv.layers.telemetry

import io.github.sndnv.layers.telemetry.analytics.AnalyticsCollector
import io.github.sndnv.layers.telemetry.metrics.MetricsProvider

import scala.reflect.ClassTag

trait TelemetryContext {
  def metrics[M <: MetricsProvider](implicit tag: ClassTag[M]): M
  def analytics: AnalyticsCollector
}
