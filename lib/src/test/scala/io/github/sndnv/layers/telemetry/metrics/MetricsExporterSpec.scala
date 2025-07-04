package io.github.sndnv.layers.telemetry.metrics

import scala.collection.mutable
import scala.concurrent.Future

import io.github.sndnv.layers.testing.UnitSpec
import io.opentelemetry.api.common.AttributeKey
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.HttpMethods
import org.apache.pekko.http.scaladsl.model.HttpRequest
import org.apache.pekko.http.scaladsl.model.HttpResponse
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal

class MetricsExporterSpec extends UnitSpec {
  "A Prometheus MetricsExporter" should "provide a Prometheus metrics endpoint" in {
    import io.github.sndnv.layers.telemetry.metrics.MeterExtensions._

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

  it should "support behaving as a proxy Prometheus registry" in {
    import io.github.sndnv.layers.telemetry.metrics.MeterExtensions._

    val port = ports.dequeue()

    val exporter = MetricsExporter.Prometheus.asProxyRegistry(instrumentation = "test", interface = "localhost", port = port) {
      registry =>
        import io.prometheus.client.Counter

        Counter
          .build("prometheus_counter_1", "Test description")
          .register(registry)
          .inc()

        Counter
          .build("prometheus_counter_2", "Test description")
          .labelNames("e")
          .register(registry)
          .labels("f")
          .inc()
    }

    exporter.meter.counter(name = "counter_3").inc()
    exporter.meter.counter(name = "counter_4").inc(AttributeKey.stringKey("c") -> "d")

    for {
      metrics <- getMetrics(metricsUrl = s"http://localhost:$port/metrics")
    } yield {
      val _ = exporter.shutdown()

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
}
