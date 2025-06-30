package io.github.sndnv.layers.testing

class GeneratorsSpec extends UnitSpec {
  "Generators" should "generate finite durations" in {
    Generators.generateFiniteDuration.isFinite should be(true)
  }

  they should "generate sequences" in {
    Generators.generateSeq(min = 2, max = 3, g = "abc").size should be(2)
  }

  they should "generate URIs" in {
    Generators.generateUri should startWith("http://")
  }

  they should "generate strings" in {
    Generators.generateString(withSize = 3).length should be(3)
  }
}
