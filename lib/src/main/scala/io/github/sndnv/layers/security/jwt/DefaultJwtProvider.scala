package io.github.sndnv.layers.security.jwt

import java.time.Instant

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import io.github.sndnv.layers.persistence.KeyValueStore
import io.github.sndnv.layers.persistence.memory.MemoryStore
import io.github.sndnv.layers.security.jwt.DefaultJwtProvider.StoredAccessTokenResponse
import io.github.sndnv.layers.security.oauth.OAuthClient
import io.github.sndnv.layers.security.oauth.OAuthClient.AccessTokenResponse
import io.github.sndnv.layers.telemetry.TelemetryContext
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

class DefaultJwtProvider(
  client: OAuthClient,
  clientParameters: OAuthClient.GrantParameters,
  expirationTolerance: FiniteDuration,
  cache: KeyValueStore[String, StoredAccessTokenResponse]
)(implicit system: ActorSystem[Nothing])
    extends JwtProvider {
  private val untypedSystem = system.classicSystem
  private implicit val ec: ExecutionContext = system.executionContext

  override def provide(scope: String): Future[String] =
    cache.get(scope).flatMap {
      case Some(response) if response.isValid(expirationTolerance) =>
        Future.successful(response.underlying.access_token)

      case _ =>
        for {
          response <- client.token(
            scope = Some(scope),
            parameters = clientParameters
          )
          _ <- cache.put(scope, StoredAccessTokenResponse(response))
        } yield {
          val _ = org.apache.pekko.pattern.after(
            duration = response.expires_in.seconds - expirationTolerance,
            using = untypedSystem.scheduler
          )(cache.delete(scope))

          response.access_token
        }
    }
}

object DefaultJwtProvider {
  def apply(
    client: OAuthClient,
    clientParameters: OAuthClient.GrantParameters,
    expirationTolerance: FiniteDuration
  )(implicit system: ActorSystem[Nothing], telemetry: TelemetryContext, timeout: Timeout): DefaultJwtProvider =
    new DefaultJwtProvider(
      client = client,
      clientParameters = clientParameters,
      expirationTolerance = expirationTolerance,
      cache = MemoryStore[String, StoredAccessTokenResponse](name = "jwt-provider-cache")
    )

  final case class StoredAccessTokenResponse(underlying: AccessTokenResponse, expiresAt: Instant) {
    def isValid(withTolerance: FiniteDuration): Boolean = expiresAt.isAfter(Instant.now().plusMillis(withTolerance.toMillis))
  }

  object StoredAccessTokenResponse {
    def apply(response: AccessTokenResponse): StoredAccessTokenResponse =
      StoredAccessTokenResponse(underlying = response, expiresAt = Instant.now().plusSeconds(response.expires_in))
  }
}
