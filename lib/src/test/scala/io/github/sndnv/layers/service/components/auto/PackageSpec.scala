package io.github.sndnv.layers.service.components.auto

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.github.sndnv.layers.service.components.Component
import io.github.sndnv.layers.service.components.ComponentLoader
import io.github.sndnv.layers.testing.UnitSpec

class PackageSpec extends UnitSpec {
  it should "support automatically unwrapping components" in {
    implicit val wrapped: Component[String] = new Component[String] {
      override def renderConfig(withPrefix: String): String = ""
      override def component: String = "test-component"
    }

    val unwrapped: String = implicitly

    unwrapped should be("test-component")
  }

  it should "support automatically wrapping existing implicit values" in {
    implicit val a: String = "abc"
    implicit val b: Int = 0
    implicit val c: Boolean = false
    implicit val d: Config = config
    implicit val e: ComponentLoader.Context[Double] = ComponentLoader.Context(value = 4.2d)

    val context1 = implicitly[ComponentLoader.Context[String]]
    val context2 = implicitly[ComponentLoader.Context[(String, Int)]]
    val context3 = implicitly[ComponentLoader.Context[(String, Int, Boolean)]]
    val context4 = implicitly[ComponentLoader.Context[(String, Int, Boolean, Config)]]
    val context5 = implicitly[ComponentLoader.Context[(String, Int, Boolean, Config, ComponentLoader.Context[Double])]]

    context1.value should be(a)
    context2.value should be((a, b))
    context3.value should be((a, b, c))
    context4.value should be((a, b, c, d))
    context5.value should be((a, b, c, d, e))
  }

  private val config = ConfigFactory.load().getConfig("io.github.sndnv.layers.test.service.components")
}
