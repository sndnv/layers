package io.github.sndnv.layers.security.oauth

import scala.collection.mutable
import scala.util.control.NonFatal

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.github.sndnv.layers.security.mocks.MockJwksGenerator
import io.github.sndnv.layers.security.mocks.MockJwtEndpoint
import io.github.sndnv.layers.security.oauth.OAuthClient.GrantParameters
import io.github.sndnv.layers.security.tls.EndpointContext
import io.github.sndnv.layers.telemetry.TelemetryContext
import io.github.sndnv.layers.telemetry.mocks.MockTelemetryContext
import io.github.sndnv.layers.testing.UnitSpec
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.scalatest.BeforeAndAfterAll

class DefaultOAuthClientSpec extends UnitSpec with BeforeAndAfterAll {
  "A DefaultOAuthClient" should "successfully retrieve tokens (client credentials)" in {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val expiration = 42
    val endpoint = createEndpoint(port = ports.dequeue(), expirationSeconds = expiration)
    endpoint.start()

    val client = createClient(endpoint = s"${endpoint.url}/token")

    client
      .token(
        scope = Some(clientId),
        parameters = GrantParameters.ClientCredentials()
      )
      .map { response =>
        endpoint.stop()

        endpoint.count("/token") should be(1)

        response.access_token should not be empty
        response.expires_in should be(expiration)
        response.scope should be(Some(clientId))

        telemetry.layers.security.oauthClient.token should be(1)
      }
  }

  it should "successfully retrieve tokens (resource owner password credentials)" in {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val expiration = 42
    val endpoint = createEndpoint(port = ports.dequeue(), expirationSeconds = expiration)
    endpoint.start()

    val client = createClient(endpoint = s"${endpoint.url}/token")

    client
      .token(
        scope = Some(clientId),
        parameters = GrantParameters.ResourceOwnerPasswordCredentials(username = user, password = userPassword)
      )
      .map { response =>
        endpoint.stop()

        endpoint.count("/token") should be(1)

        response.access_token should not be empty
        response.expires_in should be(expiration)
        response.scope should be(Some(clientId))

        telemetry.layers.security.oauthClient.token should be(1)
      }
  }

  it should "successfully retrieve tokens (refresh)" in {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val expiration = 42
    val endpoint = createEndpoint(port = ports.dequeue(), expirationSeconds = expiration)
    endpoint.start()

    val client = createClient(endpoint = s"${endpoint.url}/token")

    client
      .token(
        scope = Some(clientId),
        parameters = GrantParameters.RefreshToken(refreshToken = refreshToken)
      )
      .map { response =>
        endpoint.stop()

        endpoint.count("/token") should be(1)

        response.access_token should not be empty
        response.expires_in should be(expiration)
        response.scope should be(Some(clientId))

        telemetry.layers.security.oauthClient.token should be(1)
      }
  }

  it should "support providing no scope" in {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val expiration = 42
    val endpoint = createEndpoint(port = ports.dequeue(), expirationSeconds = expiration)
    endpoint.start()

    val client = createClient(endpoint = s"${endpoint.url}/token")

    client
      .token(
        scope = None,
        parameters = GrantParameters.ClientCredentials()
      )
      .map { response =>
        endpoint.stop()

        endpoint.count("/token") should be(1)

        response.access_token should not be empty
        response.expires_in should be(expiration)
        response.scope should be(None)

        telemetry.layers.security.oauthClient.token should be(1)
      }
  }

  it should "support providing grant parameters as form parameters" in {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val expiration = 42
    val endpoint = createEndpoint(port = ports.dequeue(), expirationSeconds = expiration)
    endpoint.start()

    val client = createClient(endpoint = s"${endpoint.url}/token", useQueryString = false)

    client
      .token(
        scope = Some(clientId),
        parameters = GrantParameters.ClientCredentials()
      )
      .map { response =>
        endpoint.stop()

        endpoint.count("/token") should be(1)

        response.access_token should not be empty
        response.expires_in should be(expiration)
        response.scope should be(Some(clientId))

        telemetry.layers.security.oauthClient.token should be(1)
      }
  }

  it should "handle token request failures" in {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val endpoint = "http://localhost/token"
    val client = createClient(endpoint)

    client
      .token(
        scope = Some(clientId),
        parameters = GrantParameters.ClientCredentials()
      )
      .map { response =>
        fail(s"Unexpected response received: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should startWith("Failed to retrieve token")
        e.getMessage should include("Connection refused")
        telemetry.layers.security.oauthClient.token should be(0)
      }
  }

  it should "handle failures during token unmarshalling" in {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val endpoint = createEndpoint(port = ports.dequeue())
    endpoint.start()

    val client = createClient(endpoint = s"${endpoint.url}/token/invalid")

    client
      .token(
        scope = Some(clientId),
        parameters = GrantParameters.ClientCredentials()
      )
      .map { response =>
        fail(s"Unexpected response received: [$response]")
      }
      .recover { case NonFatal(e) =>
        endpoint.stop()
        e.getMessage should startWith("Failed to unmarshal response [200 OK]")
        e.getMessage should include("Unsupported Content-Type")
        telemetry.layers.security.oauthClient.token should be(0)
      }
  }

  it should "handle unexpected token endpoint responses" in {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val endpoint = createEndpoint(port = ports.dequeue())
    endpoint.start()

    val tokenEndpoint = s"${endpoint.url}/token/error"
    val client = createClient(endpoint = tokenEndpoint)

    client
      .token(
        scope = Some(clientId),
        parameters = GrantParameters.ClientCredentials()
      )
      .map { response =>
        fail(s"Unexpected response received: [$response]")
      }
      .recover { case NonFatal(e) =>
        endpoint.stop()

        e.getMessage should startWith(
          s"Token retrieval from [$tokenEndpoint] failed with [500 Internal Server Error]"
        )

        telemetry.layers.security.oauthClient.token should be(0)
      }
  }

  it should "support custom connection contexts" in {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val config: Config = ConfigFactory.load().getConfig("io.github.sndnv.layers.test.security.tls")

    val serverContextConfig = EndpointContext.Config(config.getConfig("context-server-jks"))

    val clientContext = EndpointContext(
      config = EndpointContext.Config(config.getConfig("context-client"))
    )

    val expiration = 42

    val endpoint = createEndpoint(
      port = ports.dequeue(),
      expirationSeconds = expiration,
      keystoreConfig = Some(serverContextConfig)
    )

    endpoint.start()

    val client = createClient(endpoint = s"${endpoint.url}/token", context = Some(clientContext))

    client
      .token(
        scope = Some(clientId),
        parameters = GrantParameters.ClientCredentials()
      )
      .map { response =>
        endpoint.stop()

        endpoint.count("/token") should be(1)

        response.access_token should not be empty
        response.expires_in should be(expiration)
        response.scope should be(Some(clientId))

        telemetry.layers.security.oauthClient.token should be(1)
      }
  }

  private def createClient(
    endpoint: String,
    useQueryString: Boolean = true,
    context: Option[EndpointContext] = None
  )(implicit telemetry: TelemetryContext): DefaultOAuthClient =
    DefaultOAuthClient(
      tokenEndpoint = endpoint,
      client = clientId,
      clientSecret = clientSecret,
      useQueryString = useQueryString,
      context = context
    )

  private def createEndpoint(
    port: Int,
    expirationSeconds: Int = 6000,
    keystoreConfig: Option[EndpointContext.Config] = None
  ): MockJwtEndpoint =
    new MockJwtEndpoint(
      port = port,
      credentials = MockJwtEndpoint.ExpectedCredentials(
        subject = clientId,
        secret = clientSecret,
        refreshToken = refreshToken,
        user = user,
        userPassword = userPassword
      ),
      expirationSeconds = expirationSeconds.toLong,
      signatureKey = MockJwksGenerator.generateRandomRsaKey(keyId = Some("rsa-0")),
      context = keystoreConfig.map(EndpointContext.apply)
    )

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "OAuthClientSpec"
  )

  private val ports: mutable.Queue[Int] = (12000 to 12100).to(mutable.Queue)

  private val clientId: String = "some-client"
  private val clientSecret: String = "some-client-secret"
  private val refreshToken: String = "some-token"
  private val user: String = "some-user"
  private val userPassword = "some-password"

  override protected def afterAll(): Unit =
    system.terminate()
}
