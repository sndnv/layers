package io.github.sndnv.layers.service.components.internal

import io.github.sndnv.layers.testing.UnitSpec

class DynamicComponentClassLoaderSpec extends UnitSpec {
  "A DynamicComponentClassLoader" should "load components based on class names (with config)" in {
    val component = DynamicComponentClassLoader
      .load[TestComponent](
        componentClassName = "io.github.sndnv.layers.service.components.internal.TestComponentImpl1",
        componentConfig = config
      )
      .get

    component.renderConfig(withPrefix = "prefix") should be("""prefix - {"a":"b","c":"d","e":42}""")

    component.component should be(a[TestComponentImpl1])
    component.component.a should be("TestComponentImpl1")
    component.component.b should be(1)
  }

  it should "load components based on class names (without config)" in {
    val component = DynamicComponentClassLoader
      .load[TestComponent](
        componentClassName = "io.github.sndnv.layers.service.components.internal.TestComponentImpl2",
        componentConfig = config
      )
      .get

    component.renderConfig(withPrefix = "prefix") should be("none")

    component.component should be(a[TestComponentImpl2])
    component.component.a should be("TestComponentImpl2")
    component.component.b should be(2)
  }

  it should "fail to load components that don't exist" in {
    val e = DynamicComponentClassLoader
      .load[TestComponent](
        componentClassName = "io.github.sndnv.layers.service.components.internal.Other",
        componentConfig = config
      )
      .failed
      .get

    e should be(an[IllegalArgumentException])
    e.getMessage should be(
      "Failed to find component from [io.github.sndnv.layers.service.components.internal.Other]: " +
        "[ClassNotFoundException - io.github.sndnv.layers.service.components.internal.Other]"
    )
  }

  it should "fail to load components that don't match the expected component type" in {
    val e = DynamicComponentClassLoader
      .load[OtherComponent](
        componentClassName = "io.github.sndnv.layers.service.components.internal.TestComponentImpl1",
        componentConfig = config
      )
      .failed
      .get

    e should be(an[IllegalArgumentException])
    e.getMessage should be(
      "Target component [io.github.sndnv.layers.service.components.internal.TestComponentImpl1] " +
        "does not conform to expected type [io.github.sndnv.layers.service.components.internal.OtherComponent]"
    )
  }

  it should "fail to get components that don't have one of the expected constructors" in {
    val e = DynamicComponentClassLoader
      .load[TestComponent](
        componentClassName = "io.github.sndnv.layers.service.components.internal.TestComponentImpl3",
        componentConfig = config
      )
      .failed
      .get

    e should be(an[IllegalArgumentException])
    e.getMessage should be(
      "Failed to get a supported component constructor from " +
        "[io.github.sndnv.layers.service.components.internal.TestComponentImpl3]: " +
        "[NoSuchMethodException - io.github.sndnv.layers.service.components.internal.TestComponentImpl3.<init>(com.typesafe.config.Config) " +
        "or io.github.sndnv.layers.service.components.internal.TestComponentImpl3.<init>()]"
    )
  }

  it should "handle constructor failures (with config)" in {
    val e = DynamicComponentClassLoader
      .load[OtherComponent](
        componentClassName = "io.github.sndnv.layers.service.components.internal.OtherComponentImpl1",
        componentConfig = config
      )
      .failed
      .get

    e should be(an[IllegalArgumentException])
    e.getMessage should be(
      "Failed to create component instance from config-based constructor of " +
        "[io.github.sndnv.layers.service.components.internal.OtherComponentImpl1]: " +
        "[InvocationTargetException - null]"
    )
  }

  it should "handle constructor failures (without config)" in {
    val e = DynamicComponentClassLoader
      .load[OtherComponent](
        componentClassName = "io.github.sndnv.layers.service.components.internal.OtherComponentImpl2",
        componentConfig = config
      )
      .failed
      .get

    e should be(an[IllegalArgumentException])
    e.getMessage should be(
      "Failed to create component instance from no-arguments constructor of " +
        "[io.github.sndnv.layers.service.components.internal.OtherComponentImpl2]: " +
        "[InvocationTargetException - null]"
    )
  }

  private val config = com.typesafe.config.ConfigFactory
    .load()
    .getConfig("io.github.sndnv.layers.test.service.components.dynamic")
}
