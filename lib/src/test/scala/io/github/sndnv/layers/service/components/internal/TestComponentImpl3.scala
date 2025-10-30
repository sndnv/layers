package io.github.sndnv.layers.service.components.internal

import scala.annotation.unused

class TestComponentImpl3(@unused arg: String) extends TestComponent {
  override def a: String = "TestComponentImpl3"
  override def b: Int = 3
}
