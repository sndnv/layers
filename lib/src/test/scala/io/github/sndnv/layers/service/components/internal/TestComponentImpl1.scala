package io.github.sndnv.layers.service.components.internal

import com.typesafe.config.Config
import com.typesafe.config.ConfigRenderOptions

class TestComponentImpl1(config: Config) extends TestComponent {
  override def a: String = "TestComponentImpl1"
  override def b: Int = 1

  def renderConfig(withPrefix: String): String =
    s"$withPrefix - ${config.root().render(ConfigRenderOptions.concise())}"
}
