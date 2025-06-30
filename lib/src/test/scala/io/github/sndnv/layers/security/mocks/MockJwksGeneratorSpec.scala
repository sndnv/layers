package io.github.sndnv.layers.security.mocks

import scala.jdk.CollectionConverters._

import io.github.sndnv.layers.testing.UnitSpec

class MockJwksGeneratorSpec extends UnitSpec {
  "A MockJwksGenerator" should "generate JWKs" in {
    val jwks = MockJwksGenerator
      .generateJwks(
        rsaKeysCount = 1,
        ecKeysCount = 2,
        secretKeysCount = 3
      )
      .getJsonWebKeys
      .asScala

    jwks.count(_.getKeyType == "RSA") should be(1)
    jwks.count(_.getKeyType == "EC") should be(2)
    jwks.count(_.getKeyType == "oct") should be(4) // +1 because a secret key w/o an ID is always generated
  }
}
