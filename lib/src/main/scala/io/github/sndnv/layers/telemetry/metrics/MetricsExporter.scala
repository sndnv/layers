package io.github.sndnv.layers.telemetry.metrics

import scala.concurrent.Future
import scala.concurrent.Promise
import scala.util.control.NonFatal

import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.exporter.prometheus.PrometheusHttpServer
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.prometheus.metrics.model.registry.PrometheusRegistry
import org.apache.pekko.Done

trait MetricsExporter {
  def meter: Meter
  def shutdown(): Future[Done]
}

object MetricsExporter {
  class Prometheus(instrumentation: String, interface: String, port: Int) extends MetricsExporter {
    private val registry = new PrometheusRegistry()

    private val reader = PrometheusHttpServer.builder
      .setHost(interface)
      .setPort(port)
      .setPrometheusRegistry(registry)
      .build

    private val provider = SdkMeterProvider.builder
      .registerMetricReader(reader)
      .build

    override val meter: Meter = provider.get(instrumentation)

    override def shutdown(): Future[Done] = {
      val promise = Promise[Done]()

      val _ = provider
        .shutdown()
        .whenComplete { () =>
          registry.clear()
          val _ = promise.success(Done)
        }

      promise.future
    }
  }

  object Prometheus {
    def apply(instrumentation: String, interface: String, port: Int): Prometheus =
      new Prometheus(instrumentation, interface, port)

    @SuppressWarnings(Array("org.wartremover.warts.Throw"))
    def withRegistry(instrumentation: String, interface: String, port: Int)(f: PrometheusRegistry => Unit): Prometheus = {
      val exporter = Prometheus(instrumentation, interface, port)

      try {
        f(exporter.registry)
        exporter
      } catch {
        case NonFatal(e) =>
          val _ = exporter.shutdown()
          throw e
      }
    }
  }
}
