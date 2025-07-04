package io.github.sndnv.layers.security.jwt

import java.security.Key

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.util.Try
import scala.util.control.NonFatal

import io.github.sndnv.layers.security.Metrics
import io.github.sndnv.layers.security.exceptions.AuthenticationFailure
import io.github.sndnv.layers.security.keys.KeyProvider
import io.github.sndnv.layers.telemetry.TelemetryContext
import org.jose4j.jwa.AlgorithmConstraints
import org.jose4j.jwa.AlgorithmConstraints.ConstraintType
import org.jose4j.jwt.JwtClaims
import org.jose4j.jwt.consumer.JwtConsumer
import org.jose4j.jwt.consumer.JwtConsumerBuilder
import org.jose4j.jwt.consumer.JwtContext
import org.jose4j.jwx.JsonWebStructure

class DefaultJwtAuthenticator(
  provider: KeyProvider,
  audience: String,
  override val identityClaim: String,
  expirationTolerance: FiniteDuration
)(implicit ec: ExecutionContext, telemetry: TelemetryContext)
    extends JwtAuthenticator {

  private val name = s"${this.getClass.getSimpleName} - $audience"

  private val extractor: JwtConsumer = new JwtConsumerBuilder()
    .setDisableRequireSignature()
    .setSkipAllValidators()
    .setSkipSignatureVerification()
    .build()

  private val metrics = telemetry.metrics[Metrics.Authenticator]

  override def authenticate(credentials: String): Future[JwtClaims] = {
    val result = for {
      context <- Future { extractor.process(credentials) }
      keyId <- extractKeyId(context)
      key <- provider.key(id = keyId)
      claims <- process(context, key)
    } yield {
      metrics.recordAuthentication(authenticator = name, successful = true)
      claims
    }

    result
      .recoverWith { case NonFatal(e) =>
        metrics.recordAuthentication(authenticator = name, successful = false)
        Future.failed(
          AuthenticationFailure(
            s"Failed to authenticate token: [${e.getClass.getSimpleName}: ${e.getMessage}]"
          )
        )
      }
  }

  private def extractKeyId(context: JwtContext): Future[Option[String]] =
    Future.fromTry(
      Try {
        context.getJoseObjects.asScala
          .collectFirst {
            case struct: JsonWebStructure if struct.getKeyIdHeaderValue != null =>
              struct.getKeyIdHeaderValue
          }
      }
    )

  private def process(context: JwtContext, key: Key): Future[JwtClaims] =
    Future {
      val consumer = new JwtConsumerBuilder()
        .setExpectedIssuer(provider.issuer)
        .setExpectedAudience(audience)
        .setRequireSubject()
        .setRequireExpirationTime()
        .setAllowedClockSkewInSeconds(expirationTolerance.toSeconds.toInt)
        .setVerificationKey(key)
        .setJwsAlgorithmConstraints(
          new AlgorithmConstraints(
            ConstraintType.PERMIT,
            provider.allowedAlgorithms: _*
          )
        )
        .build()

      val _ = consumer.processContext(context)
      val claims = context.getJwtClaims

      require(
        claims.hasClaim(identityClaim),
        s"Required identity claim [$identityClaim] was not found in [${claims.toString}]"
      )

      claims
    }
}

object DefaultJwtAuthenticator {
  def apply(
    provider: KeyProvider,
    audience: String,
    identityClaim: String,
    expirationTolerance: FiniteDuration
  )(implicit ec: ExecutionContext, telemetry: TelemetryContext): DefaultJwtAuthenticator =
    new DefaultJwtAuthenticator(
      provider = provider,
      audience = audience,
      identityClaim = identityClaim,
      expirationTolerance = expirationTolerance
    )
}
