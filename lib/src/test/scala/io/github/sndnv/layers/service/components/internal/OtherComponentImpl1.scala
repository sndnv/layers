package io.github.sndnv.layers.service.components.internal

import scala.annotation.nowarn
import scala.annotation.unused

class OtherComponentImpl1(@unused arg: String) extends OtherComponent {
  @nowarn
  def this(config: com.typesafe.config.Config) = {
    this(arg = "")
    throw new RuntimeException("Test failure")
  }

  override def a: String = "OtherComponentImpl1"
}
