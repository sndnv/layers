package io.github.sndnv.layers.security.mocks

import scala.collection.mutable
import scala.concurrent.duration._

import io.github.sndnv.layers.testing.UnitSpec
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.HttpRequest
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.jose4j.jwk.JsonWebKeySet
import org.scalatest.BeforeAndAfterAll

class MockJwksEndpointSpec extends UnitSpec with BeforeAndAfterAll {
  "A MockJwksEndpoint" should "provide valid JWKs" in {
    val endpoint = MockJwksEndpoint(port = ports.dequeue())

    for {
      _ <- endpoint.start()
      response <- Http().singleRequest(request = HttpRequest(uri = endpoint.resolve("/valid/jwks.json")))
      json <- response.entity.toStrict(3.seconds).map(_.data.utf8String)
      _ = endpoint.stop()
    } yield {
      response.status should be(StatusCodes.OK)

      new JsonWebKeySet(json).getJsonWebKeys.size should be(endpoint.jwks.getJsonWebKeys.size)
    }
  }

  it should "provide invalid JWKs" in {
    val endpoint = MockJwksEndpoint(port = ports.dequeue())

    for {
      _ <- endpoint.start()
      response <- Http().singleRequest(request = HttpRequest(uri = endpoint.resolve("/invalid/jwks.json")))
      _ = endpoint.stop()
    } yield {
      response.status should be(StatusCodes.InternalServerError)
    }
  }

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "MockJwksEndpointSpec"
  )

  private val ports: mutable.Queue[Int] = (15000 to 15100).to(mutable.Queue)

  override protected def afterAll(): Unit =
    system.terminate()
}
