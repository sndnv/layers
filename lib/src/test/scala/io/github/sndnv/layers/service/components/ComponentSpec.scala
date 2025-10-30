package io.github.sndnv.layers.service.components

import io.github.sndnv.layers.testing.UnitSpec

class ComponentSpec extends UnitSpec {
  "A Component" should "support components without configuration" in {
    val component = Component.withoutConfig[String](comp = "abc")

    component.component should be("abc")
    component.renderConfig(withPrefix = "") should be("none")
  }
}
