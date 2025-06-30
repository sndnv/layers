package io.github.sndnv.layers.testing

import org.apache.pekko.util.ByteString

class EncodingHelpersSpec extends UnitSpec with EncodingHelpers {
  "EncodingHelpers" should "encode/decode to/from base64" in {
    val decoded = ByteString("abc")
    val encoded = "YWJj"

    decoded.encodeAsBase64 should be(encoded)
    encoded.decodeFromBase64 should be(decoded)
  }
}
