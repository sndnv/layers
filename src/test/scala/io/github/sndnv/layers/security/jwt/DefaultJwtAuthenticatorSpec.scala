package io.github.sndnv.layers.security.jwt

import io.github.sndnv.layers.UnitSpec
import io.github.sndnv.layers.security.mocks.MockJwksGenerators

class DefaultJwtAuthenticatorSpec extends UnitSpec with JwtAuthenticatorBehaviour {
  "A DefaultJwtAuthenticator" should behave like authenticator(
    withKeyType = "RSA",
    withJwk = MockJwksGenerators.generateRandomRsaKey(Some("rsa-0"))
  )

  it should behave like authenticator(
    withKeyType = "EC",
    withJwk = MockJwksGenerators.generateRandomEcKey(Some("ec-0"))
  )

  it should behave like authenticator(
    withKeyType = "Secret",
    withJwk = MockJwksGenerators.generateRandomSecretKey(Some("oct-0"))
  )
}
