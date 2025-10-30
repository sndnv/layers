package io.github.sndnv.layers.api

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._

import io.github.sndnv.layers.security.tls.EndpointContext
import io.github.sndnv.layers.testing.UnitSpec
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives
import org.apache.pekko.http.scaladsl.server.Directives._
import org.mockito.scalatest.AsyncMockitoSugar
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.slf4j.Logger

class EndpointSpec extends UnitSpec with BeforeAndAfterAll with Eventually with AsyncMockitoSugar {
  "An Endpoint" should "support starting itself" in {
    val port = ports.dequeue()

    val logger = mock[Logger]

    val endpoint = new Endpoint {
      override def name: String = "test"
      override protected def log: Logger = logger
      override def bind(interface: String, port: Int, context: Option[EndpointContext]): Future[Http.ServerBinding] =
        Http()
          .newServerAt(interface = interface, port = port)
          .bind(
            get {
              Directives.complete(StatusCodes.OK)
            }
          )
    }

    endpoint.start(interface = "localhost", port = port, context = None)

    eventually {
      verify(logger).info(
        "Endpoint [{}] successfully started on [{}:{}]",
        "test",
        "localhost",
        port
      )
    }

    succeed
  }

  it should "log binding failures" in {
    val port = 1

    val logger = mock[Logger]

    val endpoint = new Endpoint {
      override def name: String = "test"
      override protected def log: Logger = logger
      override def bind(interface: String, port: Int, context: Option[EndpointContext]): Future[Http.ServerBinding] =
        Http()
          .newServerAt(interface = interface, port = port)
          .bind(
            get {
              Directives.complete(StatusCodes.OK)
            }
          )
    }

    endpoint.start(interface = "localhost", port = port, context = None)

    eventually {
      verify(logger).error(
        "Failed to start [{}] endpoint on [{}:{}]: [{} - {}]",
        "test",
        "localhost",
        port,
        "BindException",
        "[localhost/127.0.0.1:1] Permission denied"
      )
    }

    succeed
  }

  override protected def afterAll(): Unit =
    typedSystem.terminate()

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "EndpointSpec"
  )

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 100.milliseconds)

  private val ports: mutable.Queue[Int] = (16000 to 16100).to(mutable.Queue)
}
