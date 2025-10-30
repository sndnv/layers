package io.github.sndnv.layers.service.components

/**
  * Trait representing components created by a [[ComponentLoader]].
  */
trait Component[+T] {

  /**
    * Renders the configuration (if any) of the component as a string.
    *
    * @param withPrefix whitespace prefix used for grouping and aligning config entries
    *
    * @return the rendered config
    */
  def renderConfig(withPrefix: String): String

  /**
    * The actual component.
    */
  def component: T
}

/**
  * @see [[Component]]
  */
object Component {

  /**
    * Creates a component without configuration.
    */
  def withoutConfig[T](comp: => T): Component[T] =
    new Component[T] {
      override def renderConfig(withPrefix: String): String =
        "none"

      override def component: T = comp
    }
}
