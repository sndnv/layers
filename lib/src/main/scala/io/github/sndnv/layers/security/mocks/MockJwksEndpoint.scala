package io.github.sndnv.layers.security.mocks

import java.security.PublicKey

import scala.jdk.CollectionConverters._

import io.github.sndnv.layers.api.mocks.MockEndpoint
import io.github.sndnv.layers.security.tls.EndpointContext
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import org.jose4j.jwk.JsonWebKeySet

class MockJwksEndpoint(
  override val port: Int,
  rsaKeysCount: Int,
  ecKeysCount: Int,
  secretKeysCount: Int,
  override val context: Option[EndpointContext]
)(implicit override val system: ActorSystem[Nothing])
    extends MockEndpoint {
  val jwks: JsonWebKeySet = MockJwksGenerator.generateJwks(rsaKeysCount, ecKeysCount, secretKeysCount)

  val keys: Map[String, PublicKey] =
    jwks.getJsonWebKeys.asScala
      .map(c => (c.getKeyId, c.getKey))
      .collect { case (keyId: String, key: PublicKey) =>
        (keyId, key)
      }
      .toMap

  override val routes: Route = concat(
    path("valid" / "jwks.json") {
      get {
        complete(jsonEntity(jwks.toJson()))
      }
    },
    path("invalid" / "jwks.json") {
      get {
        complete(StatusCodes.InternalServerError)
      }
    }
  )
}

object MockJwksEndpoint {
  def apply(port: Int)(implicit system: ActorSystem[Nothing]): MockJwksEndpoint =
    MockJwksEndpoint(
      port = port,
      rsaKeysCount = 3,
      ecKeysCount = 3,
      secretKeysCount = 3,
      context = None
    )

  def apply(port: Int, context: EndpointContext)(implicit system: ActorSystem[Nothing]): MockJwksEndpoint =
    MockJwksEndpoint(
      port = port,
      rsaKeysCount = 3,
      ecKeysCount = 3,
      secretKeysCount = 3,
      context = Some(context)
    )

  def apply(
    port: Int,
    rsaKeysCount: Int,
    ecKeysCount: Int,
    secretKeysCount: Int,
    context: Option[EndpointContext]
  )(implicit system: ActorSystem[Nothing]): MockJwksEndpoint =
    new MockJwksEndpoint(
      port = port,
      rsaKeysCount = rsaKeysCount,
      ecKeysCount = ecKeysCount,
      secretKeysCount = secretKeysCount,
      context = context
    )
}
