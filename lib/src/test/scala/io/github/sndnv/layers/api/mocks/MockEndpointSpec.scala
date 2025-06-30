package io.github.sndnv.layers.api.mocks

import scala.collection.mutable

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.github.sndnv.layers.security.tls.EndpointContext
import io.github.sndnv.layers.testing.UnitSpec
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.HttpMethods
import org.apache.pekko.http.scaladsl.model.HttpRequest
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.util.ByteString
import org.scalatest.BeforeAndAfterAll

class MockEndpointSpec extends UnitSpec with BeforeAndAfterAll { spec =>
  "A MockEndpoint" should "support plaintext connections" in {
    val endpoint = createEndpoint()

    endpoint.url should be(s"http://localhost:${endpoint.port}")

    for {
      _ <- endpoint.start()
      response <- Http().singleRequest(request = HttpRequest(uri = endpoint.resolve("/ok/a")))
      _ = endpoint.stop()
    } yield {
      response.status should be(StatusCodes.OK)
    }
  }

  it should "support TLS connections" in {
    val config: Config = ConfigFactory.load().getConfig("io.github.sndnv.layers.test.security.tls")

    val serverContext = EndpointContext(EndpointContext.Config(config.getConfig("context-server-jks")))
    val clientContext = EndpointContext(EndpointContext.Config(config.getConfig("context-client")))

    val endpoint = createEndpoint(endpointContext = Some(serverContext))

    endpoint.url should be(s"https://localhost:${endpoint.port}")

    for {
      _ <- endpoint.start()
      response <- Http().singleRequest(
        request = HttpRequest(uri = endpoint.resolve("/ok/a")),
        connectionContext = clientContext.connection
      )
      _ = endpoint.stop()
    } yield {
      response.status should be(StatusCodes.OK)
    }
  }

  it should "handle unhandled exceptions" in {
    val endpoint = createEndpoint()

    for {
      _ <- endpoint.start()
      response <- Http().singleRequest(request = HttpRequest(uri = endpoint.resolve("/error")))
      _ = endpoint.stop()
    } yield {
      response.status should be(StatusCodes.InternalServerError)
    }
  }

  it should "handle request rejections" in {
    val endpoint = createEndpoint()

    for {
      _ <- endpoint.start()
      response <- Http().singleRequest(request = HttpRequest(method = HttpMethods.POST, uri = endpoint.resolve("/ok/a")))
      _ = endpoint.stop()
    } yield {
      response.status should be(StatusCodes.MisdirectedRequest)
    }
  }

  it should "handle failures when starting" in {
    val endpoint = createEndpoint(endpointPort = 1)

    endpoint.start().failed.map { e =>
      e.getMessage should include("BindException")
      e.getMessage should include("Permission denied")
    }
  }

  it should "support counting requests" in {
    val endpoint = createEndpoint()

    endpoint.count("/ok/a") should be(0)
    endpoint.count("/ok/b") should be(0)
    endpoint.count("/ok/c") should be(0)
    endpoint.count("/ok/.*".r) should be(0)

    for {
      _ <- endpoint.start()
      _ <- Http().singleRequest(request = HttpRequest(uri = endpoint.resolve("/ok/a")))
      _ <- Http().singleRequest(request = HttpRequest(uri = endpoint.resolve("/ok/b")))
      _ <- Http().singleRequest(request = HttpRequest(uri = endpoint.resolve("/ok/a")))
      _ <- Http().singleRequest(request = HttpRequest(uri = endpoint.resolve("/ok/a")))
      _ = endpoint.stop()
    } yield {
      endpoint.count("/ok/a") should be(3)
      endpoint.count("/ok/b") should be(1)
      endpoint.count("/ok/c") should be(0)
      endpoint.count("/ok/.*".r) should be(4)
    }
  }

  it should "provide helper functions for creating entities" in {
    val endpoint = createEndpoint()

    val binary = endpoint.binaryEntity(content = ByteString("abc"))
    binary.data should be(ByteString("abc"))
    binary.contentType.toString() should be("application/octet-stream")

    val plaintext = endpoint.plainTextEntity(content = "abc")
    plaintext.data should be(ByteString("abc"))
    plaintext.contentType.toString() should be("text/plain; charset=UTF-8")

    val json = endpoint.jsonEntity(content = "abc")
    json.data should be(ByteString("abc"))
    json.contentType.toString() should be("application/json")
  }

  private def createEndpoint(
    endpointPort: Int = ports.dequeue(),
    endpointContext: Option[EndpointContext] = None
  ): MockEndpoint = new MockEndpoint {
    override val port: Int = endpointPort
    override implicit val system: ActorSystem[Nothing] = spec.system
    override def context: Option[EndpointContext] = endpointContext

    override val routes: Route = get {
      concat(
        path("ok" / "a") {
          Directives.complete(StatusCodes.OK)
        },
        path("ok" / "b") {
          Directives.complete(StatusCodes.OK)
        },
        path("error") {
          throw new RuntimeException("Test failure")
        }
      )
    }
  }

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "MockEndpointSpec"
  )

  private val ports: mutable.Queue[Int] = (10000 to 10100).to(mutable.Queue)

  override protected def afterAll(): Unit =
    system.terminate()
}
