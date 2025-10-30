package io.github.sndnv.layers.service.components

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import io.github.sndnv.layers.service.components.ComponentLoader.Context
import io.github.sndnv.layers.service.components.internal._
import io.github.sndnv.layers.testing.UnitSpec

class ComponentLoaderSpec extends UnitSpec {
  "A ComponentLoader" should "load components (with component name, with type)" in {
    val loader = new ComponentLoader.Required[TestComponent, ComponentLoader.Context.Empty] {
      override val name: String = "test"
      override val component: Option[String] = Some("test-component-1")

      override protected def default(
        config: Config
      )(implicit context: ComponentLoader.Context[ComponentLoader.Context.Empty]): Component[TestComponent] =
        new Component[TestComponent] {
          override def renderConfig(withPrefix: String): String = config.root().render(ConfigRenderOptions.concise())
          override def component: TestComponent = new TestComponentImpl2()
        }
    }

    loader.componentName should be("test-component-1")

    val component = loader.create(config = config)

    component.renderConfig(withPrefix = "") should be("""{"type":"default"}""")
    component.component should be(a[TestComponentImpl2])
  }

  it should "load components (without component name, with type)" in {
    val loader = new ComponentLoader.Selectable[TestComponent, ComponentLoader.Context.Empty] {
      override val name: String = "test"
      override val component: Option[String] = None

      override protected def underlying: Seq[ComponentLoader.TargetLoader[TestComponent, Context.Empty]] =
        Seq(
          new ComponentLoader.TargetLoader.Configurable[TestComponent, Context.Empty] {
            override val targetType: ComponentLoader.TargetType = ComponentLoader.TargetType.Custom(name = "other")
            override def create(config: Config)(implicit context: Context[Context.Empty]): Component[TestComponent] =
              new Component[TestComponent] {
                override def renderConfig(withPrefix: String): String = config.root().render(ConfigRenderOptions.concise())
                override def component: TestComponent = new TestComponentImpl2()
              }
          }
        )
    }

    loader.componentName should be("none")

    val component = loader.create(config = config.getConfig("test-component-6"))

    component.renderConfig(withPrefix = "") should be("""{"other":{"a":"b"},"type":"other"}""")
    component.component should be(a[TestComponentImpl2])
  }

  it should "load components (without a type)" in {
    val loader = new ComponentLoader.Required[TestComponent, ComponentLoader.Context.Empty] {
      override val name: String = "test"
      override val component: Option[String] = None

      override protected def default(
        config: Config
      )(implicit context: ComponentLoader.Context[ComponentLoader.Context.Empty]): Component[TestComponent] =
        new Component[TestComponent] {
          override def renderConfig(withPrefix: String): String = config.root().render(ConfigRenderOptions.concise())
          override def component: TestComponent = new TestComponentImpl2()
        }
    }

    loader.componentName should be("none")

    val component = loader.create(config = config)

    component.renderConfig(withPrefix = "") should include("""test-component-1""")
    component.renderConfig(withPrefix = "") should include("""test-component-2""")
    component.renderConfig(withPrefix = "") should include("""test-component-3""")

    component.component should be(a[TestComponentImpl2])
  }

  it should "fail to load components if too many loaders are provided for the same target type" in {
    val loader = new ComponentLoader.Selectable[TestComponent, ComponentLoader.Context.Empty] {
      override val name: String = "test"
      override val component: Option[String] = Some("test-component-1")

      override protected def underlying: Seq[ComponentLoader.TargetLoader[TestComponent, ComponentLoader.Context.Empty]] =
        Seq(
          new ComponentLoader.TargetLoader.Plain[TestComponent, ComponentLoader.Context.Empty] {
            override val targetType: ComponentLoader.TargetType = ComponentLoader.TargetType.Default
            override def create(): Component[TestComponent] = Component.withoutConfig(new TestComponentImpl2())
          },
          new ComponentLoader.TargetLoader.Plain[TestComponent, ComponentLoader.Context.Empty] {
            override val targetType: ComponentLoader.TargetType = ComponentLoader.TargetType.Default
            override def create(): Component[TestComponent] = Component.withoutConfig(new TestComponentImpl2())
          }
        )
    }

    val e = intercept[IllegalArgumentException](loader.create(config = config))

    e.getMessage should be("requirement failed: Too many target loaders (2) found for type [Default]")
  }

  it should "specify if dynamic component loading is allowed" in {
    val loader = new ComponentLoader.Required[TestComponent, Unit] {
      override val name: String = "test"
      override val component: Option[String] = Some("test-component-1")

      override protected def default(
        config: Config
      )(implicit context: ComponentLoader.Context[Unit]): Component[TestComponent] =
        Component.withoutConfig(new TestComponentImpl2())
    }

    loader.allowDynamic should be(true)
  }

  it should "fall back to the default component type if none is configured" in {
    val loader = new ComponentLoader.Optional[TestComponent, ComponentLoader.Context.Empty] {
      override val name: String = "test"
      override val component: Option[String] = Some("test-component-2")

      override protected def noop(): Component[TestComponent] =
        Component.withoutConfig(new TestComponentImpl2())

      override protected def default(
        config: Config
      )(implicit context: ComponentLoader.Context[ComponentLoader.Context.Empty]): Component[TestComponent] =
        Component.withoutConfig(new TestComponentImpl3(arg = "abc"))
    }

    val component = loader.create(config = config)

    component.renderConfig(withPrefix = "") should be("none")
    component.component should be(a[TestComponentImpl3])
  }

  it should "fail to load components if an unexpected target type is configured (with component name)" in {
    val loader = new ComponentLoader.Required[TestComponent, ComponentLoader.Context.Empty] {
      override val name: String = "test"
      override val component: Option[String] = Some("test-component-3")

      override protected def default(
        config: Config
      )(implicit context: ComponentLoader.Context[ComponentLoader.Context.Empty]): Component[TestComponent] =
        Component.withoutConfig(new TestComponentImpl2())
    }

    val e = intercept[IllegalArgumentException](loader.create(config = config))

    e.getMessage should be("Unexpected type [noop] provided for component [test.test-component-3]")
  }

  it should "fail to load components if an unexpected target type is configured (without component name)" in {
    val loader = new ComponentLoader.Selectable[TestComponent, ComponentLoader.Context.Empty] {
      override val name: String = "test"
      override val component: Option[String] = None

      override protected def underlying: Seq[ComponentLoader.TargetLoader[TestComponent, Context.Empty]] = Seq.empty
    }

    val e = intercept[IllegalArgumentException](loader.create(config = config))

    e.getMessage should be("Unexpected type [default] provided for component [test]")
  }

  "A Required ComponentLoader" should "provide configurable and dynamic target loaders" in {
    val loader = new ComponentLoader.Required[TestComponent, ComponentLoader.Context.Empty] {
      override val name: String = "test"
      override val component: Option[String] = Some("test-component-1")

      override protected def default(
        config: Config
      )(implicit context: ComponentLoader.Context[ComponentLoader.Context.Empty]): Component[TestComponent] =
        Component.withoutConfig(new TestComponentImpl2())
    }

    loader.targets should be(
      Seq(
        ComponentLoader.TargetType.Default,
        ComponentLoader.TargetType.Dynamic
      )
    )
  }

  it should "create components (default)" in {
    val loader = new ComponentLoader.Required[TestComponent, ComponentLoader.Context.Empty] {
      override val name: String = "test"
      override val component: Option[String] = Some("test-component-1")

      override protected def default(
        config: Config
      )(implicit context: ComponentLoader.Context[ComponentLoader.Context.Empty]): Component[TestComponent] =
        Component.withoutConfig(new TestComponentImpl2())
    }

    loader.create(config).component should be(a[TestComponentImpl2])
  }

  it should "create components (dynamic)" in {
    val loader = new ComponentLoader.Required[TestComponent, ComponentLoader.Context.Empty] {
      override val name: String = "test"
      override val component: Option[String] = Some("test-component-4")

      override protected def default(
        config: Config
      )(implicit context: ComponentLoader.Context[ComponentLoader.Context.Empty]): Component[TestComponent] =
        Component.withoutConfig(new TestComponentImpl2())
    }

    loader.create(config).component should be(a[TestComponentImpl1])
  }

  "An Optional ComponentLoader" should "provide a no-op, configurable and dynamic target loaders" in {
    val loader = new ComponentLoader.Optional[TestComponent, ComponentLoader.Context.Empty] {
      override val name: String = "test"
      override val component: Option[String] = Some("test-component-1")

      override protected def noop(): Component[TestComponent] =
        Component.withoutConfig(new TestComponentImpl3(arg = "abc"))

      override protected def default(
        config: Config
      )(implicit context: ComponentLoader.Context[ComponentLoader.Context.Empty]): Component[TestComponent] =
        Component.withoutConfig(new TestComponentImpl2())
    }

    loader.targets should be(
      Seq(
        ComponentLoader.TargetType.NoOp,
        ComponentLoader.TargetType.Default,
        ComponentLoader.TargetType.Dynamic
      )
    )
  }

  it should "create components (no-op)" in {
    val loader = new ComponentLoader.Optional[TestComponent, ComponentLoader.Context.Empty] {
      override val name: String = "test"
      override val component: Option[String] = Some("test-component-3")

      override protected def noop(): Component[TestComponent] =
        Component.withoutConfig(new TestComponentImpl3(arg = "abc"))

      override protected def default(
        config: Config
      )(implicit context: ComponentLoader.Context[ComponentLoader.Context.Empty]): Component[TestComponent] =
        Component.withoutConfig(new TestComponentImpl2())
    }

    loader.create(config).component should be(a[TestComponentImpl3])
  }

  it should "create components (default)" in {
    val loader = new ComponentLoader.Optional[TestComponent, ComponentLoader.Context.Empty] {
      override val name: String = "test"
      override val component: Option[String] = Some("test-component-1")

      override protected def noop(): Component[TestComponent] =
        Component.withoutConfig(new TestComponentImpl3(arg = "abc"))

      override protected def default(
        config: Config
      )(implicit context: ComponentLoader.Context[ComponentLoader.Context.Empty]): Component[TestComponent] =
        Component.withoutConfig(new TestComponentImpl2())
    }

    loader.create(config).component should be(a[TestComponentImpl2])
  }

  it should "create components (dynamic)" in {
    val loader = new ComponentLoader.Optional[TestComponent, ComponentLoader.Context.Empty] {
      override val name: String = "test"
      override val component: Option[String] = Some("test-component-4")

      override protected def noop(): Component[TestComponent] =
        Component.withoutConfig(new TestComponentImpl3(arg = "abc"))

      override protected def default(
        config: Config
      )(implicit context: ComponentLoader.Context[ComponentLoader.Context.Empty]): Component[TestComponent] =
        Component.withoutConfig(new TestComponentImpl2())
    }

    loader.create(config).component should be(a[TestComponentImpl1])
  }

  "A Fixed ComponentLoader" should "provide configurable target loaders" in {
    val loader = new ComponentLoader.Fixed[TestComponent, ComponentLoader.Context.Empty] {
      override val name: String = "test"
      override val component: Option[String] = Some("test-component-1")

      override protected def default(
        config: Config
      )(implicit context: ComponentLoader.Context[ComponentLoader.Context.Empty]): Component[TestComponent] =
        Component.withoutConfig(new TestComponentImpl2())
    }

    loader.targets should be(
      Seq(
        ComponentLoader.TargetType.Default
      )
    )
  }

  it should "create components (default)" in {
    val loader = new ComponentLoader.Fixed[TestComponent, ComponentLoader.Context.Empty] {
      override val name: String = "test"
      override val component: Option[String] = Some("test-component-1")

      override protected def default(
        config: Config
      )(implicit context: ComponentLoader.Context[ComponentLoader.Context.Empty]): Component[TestComponent] =
        Component.withoutConfig(new TestComponentImpl2())
    }

    loader.create(config).component should be(a[TestComponentImpl2])
  }

  "A ComponentLoader TargetType" should "support creation from string" in {
    ComponentLoader.TargetType(value = "noop") should be(ComponentLoader.TargetType.NoOp)
    ComponentLoader.TargetType(value = "disabled") should be(ComponentLoader.TargetType.NoOp)
    ComponentLoader.TargetType(value = "default") should be(ComponentLoader.TargetType.Default)
    ComponentLoader.TargetType(value = "dynamic") should be(ComponentLoader.TargetType.Dynamic)
    ComponentLoader.TargetType(value = "a") should be(ComponentLoader.TargetType.Custom("a"))
    ComponentLoader.TargetType(value = "b") should be(ComponentLoader.TargetType.Custom("b"))
    ComponentLoader.TargetType(value = "c") should be(ComponentLoader.TargetType.Custom("c"))
  }

  "A Dynamic TargetLoader" should "load classes dynamically" in {
    val loader = new ComponentLoader.TargetLoader.Dynamic[TestComponent, ComponentLoader.Context.Empty](allowed = true)

    val component = loader.create(config.getConfig("test-component-4"))

    component.renderConfig(withPrefix = "") should be(
      s""" - {"dynamic":{"class-name":"${classOf[TestComponentImpl1].getName}"},"type":"dynamic"}"""
    )
    component.component should be(a[TestComponentImpl1])
  }

  it should "not load classes when not allowed" in {
    val loader = new ComponentLoader.TargetLoader.Dynamic[TestComponent, ComponentLoader.Context.Empty](allowed = false)

    val e = intercept[IllegalArgumentException](loader.create(config.getConfig("test-component-4")))

    e.getMessage should be(s"Dynamic loading for component [${classOf[TestComponent].getName}] is not allowed")
  }

  it should "handle failures during class loading" in {
    val loader = new ComponentLoader.TargetLoader.Dynamic[TestComponent, ComponentLoader.Context.Empty](allowed = true)

    val e = intercept[IllegalArgumentException](loader.create(config.getConfig("test-component-5")))

    e.getMessage should startWith("Failed to get a supported component constructor")
  }

  "A ComponentLoader Context" should "provide an empty context" in {
    ComponentLoader.Context.empty.value should be(ComponentLoader.Context.Empty)
  }

  it should "support explicitly adding new values to an existing context" in {
    val original = ComponentLoader.Context(value = "abc")

    original.value should be("abc")
    original.withExtraValue[Double](extra = 4.2d).value should be(("abc", 4.2d))
  }

  it should "support mapping existing values" in {
    val original = ComponentLoader.Context(value = "abc")

    original.value should be("abc")
    original.map(_.toUpperCase).value should be("ABC")
    original.map(_.length).value should be(3)
  }

  private val config = ConfigFactory.load().getConfig("io.github.sndnv.layers.test.service.components")
}
