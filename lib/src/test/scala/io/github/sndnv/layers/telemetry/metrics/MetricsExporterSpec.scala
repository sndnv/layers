package io.github.sndnv.layers.telemetry.metrics

import java.util.concurrent.atomic.AtomicReference

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

import io.github.sndnv.layers.testing.UnitSpec
import io.opentelemetry.api.common.AttributeKey
import io.prometheus.metrics.model.registry.PrometheusRegistry
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.HttpMethods
import org.apache.pekko.http.scaladsl.model.HttpRequest
import org.apache.pekko.http.scaladsl.model.HttpResponse
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal
import org.scalatest.concurrent.Eventually

class MetricsExporterSpec extends UnitSpec with Eventually {
  "A Prometheus MetricsExporter" should "provide a Prometheus metrics endpoint" in {
    import io.github.sndnv.layers.telemetry.metrics.MeterExtensions.*

    val port = ports.dequeue()

    val exporter = MetricsExporter.Prometheus(instrumentation = "test", interface = "localhost", port = port)

    exporter.meter.counter(name = "counter_1").inc()
    exporter.meter.counter(name = "counter_2").inc(AttributeKey.stringKey("a") -> "b")

    for {
      metrics <- getMetrics(metricsUrl = s"http://localhost:$port/metrics")
    } yield {
      val _ = exporter.shutdown()
      metrics.filter(_.contains("counter")).sorted.toList match {
        case counter1 :: counter2 :: Nil =>
          counter1 should startWith("counter_1")
          counter2 should startWith("counter_2")

        case other =>
          fail(s"Unexpected result received: [$other]")
      }
    }
  }

  it should "support registering Prometheus collectors" in {
    import io.github.sndnv.layers.telemetry.metrics.MeterExtensions.*

    val port = ports.dequeue()

    val exporter = MetricsExporter.Prometheus.withRegistry(instrumentation = "test", interface = "localhost", port = port) {
      registry =>
        import io.prometheus.metrics.core.metrics.Counter
        import io.prometheus.metrics.core.metrics.Histogram

        Counter
          .builder()
          .name("prometheus_counter_1")
          .help("Test description")
          .register(registry)
          .inc()

        Counter
          .builder()
          .name("prometheus_counter_2")
          .help("Test description")
          .labelNames("e")
          .register(registry)
          .labelValues("f")
          .inc()

        Histogram
          .builder()
          .name("prometheus_histogram")
          .help("Test description")
          .register(registry)
          .observe(1.0d)
    }

    exporter.meter.counter(name = "counter_3").inc()
    exporter.meter.counter(name = "counter_4").inc(AttributeKey.stringKey("c") -> "d")

    for {
      metrics <- getMetrics(metricsUrl = s"http://localhost:$port/metrics")
    } yield {
      val _ = exporter.shutdown()

      metrics.exists(_.startsWith("prometheus_histogram_bucket")) should be(true)

      metrics.filter(_.contains("counter")).sorted.toList match {
        case counter3 :: counter4 :: prometheusCounter1 :: prometheusCounter2 :: Nil =>
          counter3 should startWith("counter_3")
          counter4 should startWith("counter_4")
          prometheusCounter1 should startWith("prometheus_counter_1")
          prometheusCounter2 should startWith("prometheus_counter_2")

        case other =>
          fail(s"Unexpected result received: [$other]")
      }
    }
  }

  it should "clear its registry on shutdown" in {
    val port = ports.dequeue()

    val registry = new AtomicReference[PrometheusRegistry]()

    val exporter = MetricsExporter.Prometheus.withRegistry(instrumentation = "test", interface = "localhost", port = port) {
      prometheusRegistry =>
        import io.prometheus.metrics.core.metrics.Counter

        Counter
          .builder()
          .name("prometheus_counter")
          .help("Test description")
          .register(prometheusRegistry)
          .inc()

        registry.set(prometheusRegistry)
    }

    registry.get().scrape().asScala.map(_.getMetadata.getName).toSeq should contain("prometheus_counter")

    exporter.shutdown().map { _ =>
      registry.get().scrape().asScala.toSeq should be(empty)
    }
  }

  it should "shut down if registering Prometheus collectors fails" in {
    val port = ports.dequeue()

    an[IllegalArgumentException] should be thrownBy {
      MetricsExporter.Prometheus.withRegistry(instrumentation = "test", interface = "localhost", port = port) { registry =>
        import io.prometheus.metrics.core.metrics.Counter

        Counter.builder().name("prometheus_counter").help("Test description").register(registry).inc()
        Counter.builder().name("prometheus_counter").help("Test description").register(registry).inc()
      }
    }

    val exporter = eventually {
      MetricsExporter.Prometheus(instrumentation = "test", interface = "localhost", port = port)
    }

    exporter.shutdown().map(_ => succeed)
  }

  private val ports: mutable.Queue[Int] = (14000 to 14100).to(mutable.Queue)

  private def getMetrics(
    metricsUrl: String
  ): Future[Seq[String]] =
    Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.GET,
          uri = metricsUrl
        )
      )
      .flatMap {
        case HttpResponse(StatusCodes.OK, _, entity, _) => Unmarshal(entity).to[String]
        case response                                   => fail(s"Unexpected response received: [$response]")
      }
      .map { result =>
        result.split("\n").toSeq.filterNot(_.startsWith("#"))
      }

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "MetricsExporterSpec"
  )

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 100.milliseconds)
}
