package io.github.sndnv.layers.service.components.internal

import scala.annotation.nowarn
import scala.annotation.unused

class OtherComponentImpl2(@unused arg: String) extends OtherComponent {
  @nowarn
  def this() = {
    this(arg = "")
    throw new RuntimeException("Test failure")
  }

  override def a: String = "OtherComponentImpl2"
}
