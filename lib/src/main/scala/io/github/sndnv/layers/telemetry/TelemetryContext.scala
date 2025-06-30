package io.github.sndnv.layers.telemetry

import scala.reflect.ClassTag

import io.github.sndnv.layers.telemetry.analytics.AnalyticsCollector
import io.github.sndnv.layers.telemetry.metrics.MetricsProvider

trait TelemetryContext {
  def metrics[M <: MetricsProvider](implicit tag: ClassTag[M]): M
  def analytics: AnalyticsCollector
}
