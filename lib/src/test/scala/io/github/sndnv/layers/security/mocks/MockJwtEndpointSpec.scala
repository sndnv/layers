package io.github.sndnv.layers.security.mocks

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._

import io.github.sndnv.layers.security.keys.Generators
import io.github.sndnv.layers.testing.UnitSpec
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.FormData
import org.apache.pekko.http.scaladsl.model.HttpMethods
import org.apache.pekko.http.scaladsl.model.HttpRequest
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.headers.BasicHttpCredentials
import org.jose4j.jws.AlgorithmIdentifiers
import org.scalatest.BeforeAndAfterAll

class MockJwtEndpointSpec extends UnitSpec with BeforeAndAfterAll {
  "A MockJwtEndpoint" should "provide JWT responses" in {
    val credentials = MockJwtEndpoint.ExpectedCredentials(
      subject = "subject",
      secret = "secret",
      refreshToken = "token",
      user = "user",
      userPassword = "password"
    )

    val basicCredentials = BasicHttpCredentials(username = credentials.subject, password = credentials.secret)

    val endpoint = MockJwtEndpoint(
      port = ports.dequeue(),
      credentials = credentials,
      expirationSeconds = 3,
      signatureKey = Generators.generateRandomSecretKey(keyId = None, algorithm = AlgorithmIdentifiers.HMAC_SHA256)
    )

    def request(withQuery: String): Future[String] =
      Http()
        .singleRequest(
          request = HttpRequest(
            method = HttpMethods.POST,
            uri = endpoint.resolve(s"/token?$withQuery")
          ).addCredentials(basicCredentials)
        )
        .flatMap(_.entity.toStrict(1.second))
        .map(_.data.utf8String)

    for {
      _ <- endpoint.start()
      response1 <- request(
        withQuery = s"scope=${credentials.subject}&grant_type=client_credentials"
      )
      response2 <- request(
        withQuery = s"grant_type=client_credentials"
      )
      response3 <- request(
        withQuery =
          s"scope=${credentials.subject}&grant_type=password&username=${credentials.user}&password=${credentials.userPassword}"
      )
      response4 <- request(
        withQuery = s"scope=${credentials.subject}&grant_type=refresh_token&refresh_token=${credentials.refreshToken}"
      )
      response5 <- Http()
        .singleRequest(
          request = HttpRequest(
            method = HttpMethods.POST,
            uri = endpoint.resolve("/token"),
            entity = FormData("grant_type" -> "client_credentials", "scope" -> credentials.subject).toEntity
          ).addCredentials(basicCredentials)
        )
        .flatMap(_.entity.toStrict(1.second))
        .map(_.data.utf8String)
      response6 <- Http()
        .singleRequest(
          request = HttpRequest(
            method = HttpMethods.POST,
            uri = endpoint.resolve("/token"),
            entity = FormData("grant_type" -> "other", "scope" -> credentials.subject).toEntity
          ).addCredentials(basicCredentials)
        )
      response7 <- Http()
        .singleRequest(
          request =
            HttpRequest(method = HttpMethods.POST, uri = endpoint.resolve("/token/invalid")).addCredentials(basicCredentials)
        )
        .flatMap(_.entity.toStrict(1.second))
        .map(_.data.utf8String)
      response8 <- Http()
        .singleRequest(
          request =
            HttpRequest(method = HttpMethods.POST, uri = endpoint.resolve("/token/error")).addCredentials(basicCredentials)
        )
      response9 <- Http()
        .singleRequest(
          request = HttpRequest(method = HttpMethods.POST, uri = endpoint.resolve("/token"))
        )
      _ = endpoint.stop()
    } yield {
      response1 should include("access_token")
      response1 should include("expires_in")

      response2 should include("access_token")
      response2 should include("expires_in")

      response3 should include("access_token")
      response3 should include("expires_in")

      response4 should include("access_token")
      response4 should include("expires_in")

      response5 should include("access_token")
      response5 should include("expires_in")

      response6.status should be(StatusCodes.BadRequest)

      response7 should be("<test>data</test>")

      response8.status should be(StatusCodes.InternalServerError)

      response9.status should be(StatusCodes.Unauthorized)

    }
  }

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "MockJwtEndpointSpec"
  )

  private val ports: mutable.Queue[Int] = (16000 to 16100).to(mutable.Queue)

  override protected def afterAll(): Unit =
    system.terminate()
}
