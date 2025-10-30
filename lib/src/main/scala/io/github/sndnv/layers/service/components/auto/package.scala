package io.github.sndnv.layers.service.components

import io.github.sndnv.layers.service.components.ComponentLoader.Context

/**
  * Allows automatic wrapping and unwrapping of context and component values.
  *
  * @see [[Context]] for more information and examples
  */
package object auto {
  implicit def unwrappedComponent[T](implicit c: Component[T]): T =
    c.component

  implicit def anyValueToContext[V](implicit
    value: V
  ): Context[V] = new Context(underlying = value)

  implicit def anyTwoValuesToContext[V1, V2](implicit
    value1: V1,
    value2: V2
  ): Context[(V1, V2)] = new Context(underlying = (value1, value2))

  implicit def anyThreeValuesToContext[V1, V2, V3](implicit
    value1: V1,
    value2: V2,
    value3: V3
  ): Context[(V1, V2, V3)] = new Context(underlying = (value1, value2, value3))

  implicit def anyFourValuesToContext[V1, V2, V3, V4](implicit
    value1: V1,
    value2: V2,
    value3: V3,
    value4: V4
  ): Context[(V1, V2, V3, V4)] = new Context(underlying = (value1, value2, value3, value4))

  implicit def anyFiveValuesToContext[V1, V2, V3, V4, V5](implicit
    value1: V1,
    value2: V2,
    value3: V3,
    value4: V4,
    value5: V5
  ): Context[(V1, V2, V3, V4, V5)] = new Context(underlying = (value1, value2, value3, value4, value5))
}
