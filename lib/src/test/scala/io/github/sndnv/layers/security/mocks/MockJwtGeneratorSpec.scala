package io.github.sndnv.layers.security.mocks

import io.github.sndnv.layers.security.keys.Generators
import io.github.sndnv.layers.testing.EncodingHelpers
import io.github.sndnv.layers.testing.UnitSpec
import org.jose4j.jws.AlgorithmIdentifiers

class MockJwtGeneratorSpec extends UnitSpec with EncodingHelpers {
  "A MockJwtGenerator" should "generate JWTs" in withRetry {
    val jwt = MockJwtGenerator.generateJwt(
      issuer = "test-issuer",
      audience = "test-audience",
      subject = "test-subject",
      signatureKey = Generators.generateRandomSecretKey(keyId = None, algorithm = AlgorithmIdentifiers.HMAC_SHA256),
      customClaims = Map("a" -> "b")
    )

    jwt.split('.').map(e => new String(e.decodeFromBase64.toArray)).toList match {
      case header :: payload :: signature :: Nil =>
        header should be("""{"kid":null,"alg":"HS256"}""")

        payload should include(""""iss":"test-issuer"""")
        payload should include(""""aud":"test-audience"""")
        payload should include(""""sub":"test-subject"""")
        payload should include(""""a":"b"""")

        signature should not be empty

      case other =>
        fail(s"Unexpected result received: [$other]")
    }
  }
}
