package io.github.sndnv.layers.telemetry.metrics

import scala.concurrent.Future
import scala.concurrent.Promise

import io.github.sndnv.layers.telemetry.metrics.internal.PrometheusProxyRegistry
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.exporter.prometheus.PrometheusHttpServer
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.prometheus.client.CollectorRegistry
import org.apache.pekko.Done

trait MetricsExporter {
  def meter: Meter
  def shutdown(): Future[Done]
}

object MetricsExporter {
  class Prometheus(instrumentation: String, interface: String, port: Int) extends MetricsExporter {
    private val reader = PrometheusHttpServer.builder
      .setHost(interface)
      .setPort(port)
      .build

    private val provider = SdkMeterProvider.builder
      .registerMetricReader(reader)
      .registerMetricReader(proxy)
      .build

    override val meter: Meter = provider.get(instrumentation)

    lazy val proxy: PrometheusProxyRegistry = PrometheusProxyRegistry(meter)

    override def shutdown(): Future[Done] = {
      val promise = Promise[Done]()

      val _ = provider
        .shutdown()
        .whenComplete { () =>
          proxy.clear()
          val _ = promise.success(Done)
        }

      promise.future
    }
  }

  object Prometheus {
    def apply(instrumentation: String, interface: String, port: Int): Prometheus =
      new Prometheus(instrumentation, interface, port)

    def asProxyRegistry(instrumentation: String, interface: String, port: Int)(f: CollectorRegistry => Unit): Prometheus = {
      val exporter = Prometheus(instrumentation, interface, port)
      f(exporter.proxy)
      exporter
    }
  }
}
