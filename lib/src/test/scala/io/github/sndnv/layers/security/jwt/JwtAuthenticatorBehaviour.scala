package io.github.sndnv.layers.security.jwt

import java.security.Key

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal

import io.github.sndnv.layers.security.keys.KeyProvider
import io.github.sndnv.layers.security.mocks.MockJwtGenerator
import io.github.sndnv.layers.telemetry.mocks.MockTelemetryContext
import io.github.sndnv.layers.testing.UnitSpec
import org.jose4j.jwk.JsonWebKey
import org.jose4j.jws.AlgorithmIdentifiers

trait JwtAuthenticatorBehaviour {
  _: UnitSpec =>
  def authenticator(withKeyType: String, withJwk: JsonWebKey): Unit = {
    it should s"successfully authenticate valid tokens ($withKeyType)" in {
      implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

      val authenticator = DefaultJwtAuthenticator(
        provider = createKeyProvider(withJwk),
        audience = "self",
        identityClaim = "sub",
        expirationTolerance = 10.seconds
      )

      val expectedSubject = "some-subject"
      val token: String = MockJwtGenerator.generateJwt(
        issuer = "self",
        audience = "self",
        subject = expectedSubject,
        signatureKey = withJwk
      )

      for {
        claims <- authenticator.authenticate(credentials = token)
      } yield {
        claims.getSubject should be(expectedSubject)
        telemetry.layers.security.authenticator.authentication should be(1)
      }
    }

    it should s"refuse authentication attempts with invalid tokens ($withKeyType)" in {
      implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

      val authenticator = DefaultJwtAuthenticator(
        provider = createKeyProvider(withJwk),
        audience = "self",
        identityClaim = "sub",
        expirationTolerance = 10.seconds
      )

      val actualAudience = "some-audience"
      val token: String = MockJwtGenerator.generateJwt(
        issuer = "self",
        audience = actualAudience,
        subject = "some-subject",
        signatureKey = withJwk
      )

      authenticator
        .authenticate(credentials = token)
        .map { response =>
          fail(s"Received unexpected response from authenticator: [$response]")
        }
        .recover { case NonFatal(e) =>
          e.getMessage should startWith("Failed to authenticate token")

          e.getMessage should include(
            s"Audience (aud) claim [$actualAudience] doesn't contain an acceptable identifier"
          )

          telemetry.layers.security.authenticator.authentication should be(1)
        }
    }

    it should s"successfully authenticate valid tokens with custom identity claims ($withKeyType)" in {
      implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

      val customIdentityClaim = "identity"

      val authenticator = DefaultJwtAuthenticator(
        provider = createKeyProvider(withJwk),
        audience = "self",
        identityClaim = "sub",
        expirationTolerance = 10.seconds
      )

      val expectedSubject = "some-subject"
      val expectedIdentity = "some-identity"
      val token: String = MockJwtGenerator.generateJwt(
        issuer = "self",
        audience = "self",
        subject = expectedSubject,
        signatureKey = withJwk,
        customClaims = Map(customIdentityClaim -> expectedIdentity)
      )

      for {
        claims <- authenticator.authenticate(credentials = token)
      } yield {
        claims.getSubject should be(expectedSubject)
        claims.getClaimValue(customIdentityClaim, classOf[String]) should be(expectedIdentity)
        telemetry.layers.security.authenticator.authentication should be(1)
      }
    }

    it should s"refuse authentication attempts when tokens have missing custom identity claims ($withKeyType)" in {
      implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

      val customIdentityClaim = "identity"

      val authenticator = DefaultJwtAuthenticator(
        provider = createKeyProvider(withJwk),
        audience = "self",
        identityClaim = customIdentityClaim,
        expirationTolerance = 10.seconds
      )

      val token: String = MockJwtGenerator.generateJwt(
        issuer = "self",
        audience = "self",
        subject = "some-subject",
        signatureKey = withJwk
      )

      authenticator
        .authenticate(credentials = token)
        .map { response =>
          fail(s"Received unexpected response from authenticator: [$response]")
        }
        .recover { case NonFatal(e) =>
          e.getMessage should startWith("Failed to authenticate token")

          e.getMessage should include(
            s"Required identity claim [$customIdentityClaim] was not found"
          )

          telemetry.layers.security.authenticator.authentication should be(1)
        }
    }
  }

  private def createKeyProvider(jwk: JsonWebKey) = new KeyProvider {
    override def key(id: Option[String]): Future[Key] = Future.successful(jwk.getKey)

    override def issuer: String = "self"

    override def allowedAlgorithms: Seq[String] =
      Seq(
        AlgorithmIdentifiers.HMAC_SHA256,
        AlgorithmIdentifiers.HMAC_SHA384,
        AlgorithmIdentifiers.HMAC_SHA512,
        AlgorithmIdentifiers.RSA_USING_SHA256,
        AlgorithmIdentifiers.RSA_USING_SHA384,
        AlgorithmIdentifiers.RSA_USING_SHA512,
        AlgorithmIdentifiers.ECDSA_USING_P256_CURVE_AND_SHA256,
        AlgorithmIdentifiers.ECDSA_USING_P384_CURVE_AND_SHA384,
        AlgorithmIdentifiers.ECDSA_USING_P521_CURVE_AND_SHA512
      )
  }
}
