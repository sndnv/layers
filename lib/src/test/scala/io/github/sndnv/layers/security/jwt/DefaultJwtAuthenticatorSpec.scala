package io.github.sndnv.layers.security.jwt

import io.github.sndnv.layers.security.mocks.MockJwksGenerator
import io.github.sndnv.layers.testing.UnitSpec

class DefaultJwtAuthenticatorSpec extends UnitSpec with JwtAuthenticatorBehaviour {
  "A DefaultJwtAuthenticator" should behave like authenticator(
    withKeyType = "RSA",
    withJwk = MockJwksGenerator.generateRandomRsaKey(Some("rsa-0"))
  )

  it should behave like authenticator(
    withKeyType = "EC",
    withJwk = MockJwksGenerator.generateRandomEcKey(Some("ec-0"))
  )

  it should behave like authenticator(
    withKeyType = "Secret",
    withJwk = MockJwksGenerator.generateRandomSecretKey(Some("oct-0"))
  )
}
