package io.github.sndnv.layers.security.mocks

import io.github.sndnv.layers.api.mocks.MockEndpoint
import io.github.sndnv.layers.security.tls.EndpointContext
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.headers.BasicHttpCredentials
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import org.jose4j.jwk.JsonWebKey

class MockJwtEndpoint(
  override val port: Int,
  credentials: MockJwtEndpoint.ExpectedCredentials,
  expirationSeconds: Long,
  signatureKey: JsonWebKey,
  override val context: Option[EndpointContext]
)(implicit override val system: ActorSystem[Nothing])
    extends MockEndpoint {
  private val clientCredentialsQuery =
    s"scope=${credentials.subject}&grant_type=client_credentials"

  private val clientCredentialsQueryWithoutScope =
    s"grant_type=client_credentials"

  private val resourceOwnerPasswordCredentialsQuery =
    s"scope=${credentials.subject}&grant_type=password&username=${credentials.user}&password=${credentials.userPassword}"

  private val refreshQuery =
    s"scope=${credentials.subject}&grant_type=refresh_token&refresh_token=${credentials.refreshToken}"

  override val routes: Route = extractCredentials {
    case Some(BasicHttpCredentials(credentials.subject, credentials.secret)) =>
      pathPrefix("token") {
        post {
          concat(
            pathEndOrSingleSlash {
              extractRequest { request =>
                request.uri.rawQueryString match {
                  case Some(`clientCredentialsQuery`) =>
                    complete(
                      jsonEntity(
                        content = s"""
                                     |{
                                     |  "access_token": "${generateJwt()}",
                                     |  "expires_in": ${expirationSeconds.toString},
                                     |  "scope": "${credentials.subject}"
                                     |}
                                   """.stripMargin
                      )
                    )

                  case Some(`clientCredentialsQueryWithoutScope`) =>
                    complete(
                      jsonEntity(
                        content = s"""
                                     |{
                                     |  "access_token": "${generateJwt()}",
                                     |  "expires_in": ${expirationSeconds.toString}
                                     |}
                                   """.stripMargin
                      )
                    )

                  case Some(`resourceOwnerPasswordCredentialsQuery`) =>
                    complete(
                      jsonEntity(
                        content = s"""
                                     |{
                                     |  "access_token": "${generateJwt()}",
                                     |  "expires_in": ${expirationSeconds.toString},
                                     |  "scope": "${credentials.subject}"
                                     |}
                                   """.stripMargin
                      )
                    )

                  case Some(`refreshQuery`) =>
                    complete(
                      jsonEntity(
                        content = s"""
                                     |{
                                     |  "access_token": "${generateJwt()}",
                                     |  "expires_in": ${expirationSeconds.toString},
                                     |  "scope": "${credentials.subject}"
                                     |}
                                   """.stripMargin
                      )
                    )

                  case _ =>
                    formFields("grant_type".as[String], "scope".as[String].?) {
                      case ("client_credentials", Some(credentials.subject)) =>
                        complete(
                          jsonEntity(
                            content = s"""
                                         |{
                                         |  "access_token": "${generateJwt()}",
                                         |  "expires_in": ${expirationSeconds.toString},
                                         |  "scope": "${credentials.subject}"
                                         |}
                                       """.stripMargin
                          )
                        )

                      case _ =>
                        complete(StatusCodes.BadRequest)
                    }
                }
              }
            },
            path("invalid") {
              complete("<test>data</test>")
            },
            path("error") {
              complete(StatusCodes.InternalServerError)
            }
          )
        }
      }

    case _ =>
      log.error("No or invalid credentials provided")
      complete(StatusCodes.Unauthorized)
  }

  private def generateJwt(): String =
    MockJwtGenerator.generateJwt(
      issuer = "some-issuer",
      audience = "some-audience",
      subject = credentials.subject,
      signatureKey = signatureKey
    )
}

object MockJwtEndpoint {
  def apply(
    port: Int,
    credentials: MockJwtEndpoint.ExpectedCredentials,
    expirationSeconds: Long,
    signatureKey: JsonWebKey
  )(implicit system: ActorSystem[Nothing]): MockJwtEndpoint =
    MockJwtEndpoint(
      port = port,
      credentials = credentials,
      expirationSeconds = expirationSeconds,
      signatureKey = signatureKey,
      context = None
    )

  def apply(
    port: Int,
    credentials: MockJwtEndpoint.ExpectedCredentials,
    expirationSeconds: Long,
    signatureKey: JsonWebKey,
    context: Option[EndpointContext]
  )(implicit system: ActorSystem[Nothing]): MockJwtEndpoint =
    new MockJwtEndpoint(
      port = port,
      credentials = credentials,
      expirationSeconds = expirationSeconds,
      signatureKey = signatureKey,
      context = context
    )

  final case class ExpectedCredentials(
    subject: String,
    secret: String,
    refreshToken: String,
    user: String,
    userPassword: String
  )
}
