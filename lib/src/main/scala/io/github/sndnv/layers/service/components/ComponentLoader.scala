package io.github.sndnv.layers.service.components

import scala.reflect.ClassTag
import scala.util.Failure
import scala.util.Success

import com.typesafe.config.Config
import io.github.sndnv.layers.service.components.internal.DynamicComponentClassLoader

/**
  * A generic component loader aiming to simplify how services are loaded and configured.
  * <br/><br/>
  * <strong>Motivation</strong> - services can become large and complex, with many pieces (components)
  * needing configuration and initialization. This is made even more challenging by certain components
  * being optional (i.e. can be turned off or disabled) or having multiple implementations.
  * <br/><br/>
  * <strong>Usage</strong>:
  *  <ul>
  *    <li>[[ComponentLoader.Required]] - for components that must be present (default or dynamically loaded)</li>
  *    <li>[[ComponentLoader.Optional]] - for components that may be turned off (disabled, default or dynamically loaded)</li>
  *    <li>[[ComponentLoader.Fixed]] - for components that must be present but not dynamically loaded</li>
  *    <li>[[ComponentLoader.Selectable]] - for any other use-case</li>
  *  </ul>
  *  <br/><br/>
  * <strong>Example</strong>:
  *  {{{
  *    // ... in EventsComponentLoader.scala
  *    object EventsComponentLoader extends ComponentLoader.Optional[EventCollector, ActorSystem[_]] {
  *      override val name: String = "events"
  *      override val component: String = "collector"
  *
  *      override protected def noop(): Component[EventCollector] =
  *        Component.withoutConfig(EventCollector.NoOp)
  *
  *      override protected def default(
  *        config: Config
  *      )(implicit context: Context[ActorSystem[_]]): Component[EventCollector] = {
  *        // if needed, arbitrary parameters can be provided via the Context[_]
  *        implicit val (a, b) = context.value // if multiple values are provided
  *        // OR
  *        import context.value // if a single value is provided (as is the case here)
  *
  *        ???
  *      }
  *    }
  *
  *    // ... in Service.scala
  *    class Service extends App {
  *      implicit system: ActorSystem[_] = ???
  *      val config: com.typesafe.config.Config = system.settings.config
  *
  *      val events: Component[EventCollector] =
  *        EventsComponentLoader.create(config = config.getConfig("service.events"))
  *
  *       // accessing the component directly
  *       events.component
  *
  *       // printing the component configuration
  *       println(
  *         s"""
  *           |\${EventsComponentLoader.name}:
  *           |  \${EventsComponentLoader.component}: \${events.renderConfig("    ")}
  *         """.stripMargin
  *       )
  *    }
  *
  *    // ... in application.conf
  *    service {
  *      events {
  *        collector {
  *          type = "default" # one of [noop, default, dynamic]
  *
  *          default {
  *            // ... config options for the default collector ...
  *          }
  *
  *          dynamic {
  *            class-name = "a.b.c.CustomEventCollector"
  *          }
  *        }
  *      }
  *    }
  *  }}}
  * @see [[ComponentLoader.Context]] for details on context creation and propagation
  * @see [[ComponentLoader.TargetLoader.Dynamic]] for details on dynamic component loading
  */
sealed trait ComponentLoader[Comp, Ctx] {
  import ComponentLoader._

  /**
    * Component loader name.
    */
  def name: String

  /**
    * Component name.
    * <br/><br/>
    * Used for retrieving the configuration of this specific component. If not provided,
    * the configuration is provided as-is.
    * <br/><br/>
    * <b>Note</b>: Not setting a specific component (name) is useful in cases where
    * multiple "pieces" form one logical group or component.
    */
  def component: Option[String]

  /**
    * Provides the component name or "`none`", if no component is specified.
    *
    * @return the component name or "`none`"
    *
    * @see [[component]] for more information
    */
  def componentName: String = component.getOrElse("none")

  /**
    * Determines if dynamic component loading is allowed.
    * <br/><br/>
    * Set to `false` if loading components dynamically should be disabled.
    * Default is `true` - dynamic loading is allowed.
    */
  def allowDynamic: Boolean = true

  /**
    * Underlying target (component) loaders.
    * <br/><br/>
    * Each [[ComponentLoader.TargetLoader]] is responsible for providing a specific type of the required
    * component `Comp`; it is decided at runtime which one to load, based on configuration.
    */
  protected def underlying: Seq[TargetLoader[Comp, Ctx]]

  /**
    * Creates a new component based on the provided configuration.
    *
    * @param config component configuration
    * @return the created component
    */
  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def create(
    config: Config
  )(implicit tag: ClassTag[Comp], context: Context[Ctx]): Component[Comp] = {
    underlying.groupBy(_.targetType).foreach { case (targetType, loaders) =>
      require(
        loaders.length == 1,
        s"Too many target loaders (${loaders.length.toString}) found for type [${targetType.toString}]"
      )
    }

    val componentTypeName = component match {
      case Some(c) if config.hasPath(s"$c.type") => config.getString(s"$c.type").trim.toLowerCase
      case None if config.hasPath("type")        => config.getString("type").trim.toLowerCase
      case _                                     => "default"
    }

    val componentType = TargetType(value = componentTypeName)

    def componentConfig: Config = component.map(config.getConfig).getOrElse(config)

    underlying.find(_.targetType == componentType) match {
      case Some(plain: TargetLoader.Plain[Comp, Ctx])               => plain.create()
      case Some(configurable: TargetLoader.Configurable[Comp, Ctx]) => configurable.create(componentConfig)(context)
      case Some(dynamic: TargetLoader.Dynamic[Comp, Ctx])           => dynamic.create(componentConfig)(tag)

      case None =>
        throw new IllegalArgumentException(
          component match {
            case Some(c) => s"Unexpected type [$componentTypeName] provided for component [$name.$c]"
            case None    => s"Unexpected type [$componentTypeName] provided for component [$name]"
          }
        )
    }
  }

  private[components] def targets: Seq[TargetType] =
    underlying.map(_.targetType)
}

/**
  * @see [[ComponentLoader]]
  */
object ComponentLoader {

  /**
    * Loader for components that must be present (default or dynamically loaded).
    *
    * @see [[ComponentLoader]]
    */
  trait Required[Comp, Ctx] extends ComponentLoader[Comp, Ctx] {

    /**
      * Creates the default component with the provided configuration and context.
      *
      * @param config configuration for the component
      * @param context component context
      *
      * @return the created component
      */
    protected def default(config: Config)(implicit context: Context[Ctx]): Component[Comp]

    override protected val underlying: Seq[TargetLoader[Comp, Ctx]] = Seq(
      new TargetLoader.Configurable[Comp, Ctx] {
        override val targetType: TargetType = TargetType.Default
        override def create(config: Config)(implicit context: Context[Ctx]): Component[Comp] = default(config)
      },
      new TargetLoader.Dynamic[Comp, Ctx](allowed = allowDynamic)
    )
  }

  /**
    * Loader for components that may be turned off (disabled, default or dynamically loaded)
    *
    * @see [[ComponentLoader]]
    */
  trait Optional[Comp, Ctx] extends ComponentLoader[Comp, Ctx] {

    /**
      * Creates the no-op/disabled component.
      *
      * @return the created component
      */
    protected def noop(): Component[Comp]

    /**
      * Creates the default component with the provided configuration and context.
      *
      * @param config configuration for the component
      * @param context component context
      *
      * @return the created component
      */
    protected def default(config: Config)(implicit context: Context[Ctx]): Component[Comp]

    override protected val underlying: Seq[TargetLoader[Comp, Ctx]] = Seq(
      new TargetLoader.Plain[Comp, Ctx] {
        override val targetType: TargetType = TargetType.NoOp
        override def create(): Component[Comp] = noop()
      },
      new TargetLoader.Configurable[Comp, Ctx] {
        override val targetType: TargetType = TargetType.Default
        override def create(config: Config)(implicit context: Context[Ctx]): Component[Comp] = default(config)
      },
      new TargetLoader.Dynamic[Comp, Ctx](allowed = allowDynamic)
    )
  }

  /**
    * Loader for components that must be present (default-only, without dynamic loading).
    *
    * @see [[ComponentLoader]]
    */
  trait Fixed[Comp, Ctx] extends ComponentLoader[Comp, Ctx] {

    /**
      * Creates the component with the provided configuration and context.
      *
      * @param config configuration for the component
      * @param context component context
      *
      * @return the created component
      */
    protected def default(config: Config)(implicit context: Context[Ctx]): Component[Comp]

    override protected val underlying: Seq[TargetLoader[Comp, Ctx]] = Seq(
      new TargetLoader.Configurable[Comp, Ctx] {
        override val targetType: TargetType = TargetType.Default
        override def create(config: Config)(implicit context: Context[Ctx]): Component[Comp] = default(config)
      }
    )
  }

  /**
    * Loader for components that may be fully customized.
    *
    * @see [[ComponentLoader]]
    */
  trait Selectable[Comp, Ctx] extends ComponentLoader[Comp, Ctx]

  sealed trait TargetType
  object TargetType {
    def apply(value: String): TargetType =
      value match {
        case "noop" | "disabled" => NoOp
        case "default"           => Default
        case "dynamic"           => Dynamic
        case other               => Custom(name = other)
      }

    final case object NoOp extends TargetType
    final case object Default extends TargetType
    final case object Dynamic extends TargetType
    final case class Custom(name: String) extends TargetType
  }

  /**
    * Target (component) loader wrapper.
    * <br/><br/>
    * Allows for distinguishing between different types of components
    * and their creation needs (i.e. no config needed, config and
    * context needed or dynamically loaded).
    * @see [[TargetLoader.Plain]] loading components without configuration or context
    * @see [[TargetLoader.Configurable]] loading components with configuration and context
    * @see [[TargetLoader.Dynamic]] loading components dynamically, at runtime
    *      (with configuration but without context)
    */
  sealed trait TargetLoader[Comp, Ctx] {
    def targetType: TargetType
  }

  /**
    * @see [[TargetLoader]]
    */
  object TargetLoader {

    /**
      * Basic target loader that does not provide configuration or context parameters.
      * <br/><br/>
      * Used primarily for providing "no-op" or disabled components.
      */
    trait Plain[Comp, Ctx] extends TargetLoader[Comp, Ctx] {
      def create(): Component[Comp]
    }

    /**
      * Target loader that provides both the current component's configuration and a context.
      */
    trait Configurable[Comp, Ctx] extends TargetLoader[Comp, Ctx] {
      def create(config: Config)(implicit context: Context[Ctx]): Component[Comp]
    }

    /**
      * Target loader capable of creating components at runtime, based on provided classes (class names).
      * <br/><br/>
      * This loader provides the ability to extend the behaviour of a service/system by loading external
      * components, as long as the relevant JARs are in the classpath.
      *  <br/><br/>
      * <strong>Component Requirements</strong>:<br/>
      * For an external class to be loaded successfully it needs to:
      * <ul>
      *   <li>Have an appropriate constructor - either without any arguments or with a single argument
      *   of type `com.typesafe.config.Config`</li>
      *   <li>Be a subclass of the specific component type `Comp`</li>
      * </ul>
      *  <br/><br/>
      * <strong>Example</strong>:
      * {{{
      *   package a.b.c
      *
      *   class CustomEventCollector1(config: com.typesafe.config.Config) extends EventCollector { ... }
      *   // OR
      *   class CustomEventCollector2() extends EventCollector { ... }
      *
      *    // ... in application.conf
      *    service {
      *      events {
      *        collector {
      *          type = "dynamic" # one of [noop, default, dynamic]
      *
      *          dynamic {
      *            // both CustomEventCollector1 and CustomEventCollector2 can be loaded here
      *            class-name = "a.b.c.CustomEventCollector1"
      *          }
      *        }
      *      }
      *    }
      * }}}
      */
    sealed class Dynamic[Comp, Ctx](allowed: => Boolean) extends TargetLoader[Comp, Ctx] {
      override val targetType: TargetType = TargetType.Dynamic

      @SuppressWarnings(Array("org.wartremover.warts.Throw"))
      def create(config: Config)(implicit tag: ClassTag[Comp]): Component[Comp] =
        if (allowed) {
          val className = config.getString("dynamic.class-name")

          DynamicComponentClassLoader.load[Comp](componentClassName = className, componentConfig = config)(tag) match {
            case Success(component) => component
            case Failure(e)         => throw e
          }
        } else {
          throw new IllegalArgumentException(s"Dynamic loading for component [${tag.runtimeClass.getName}] is not allowed")
        }
    }
  }

  /**
    * Wrapper class providing context/implicit values needed for component creation.
    * <br/><br/>
    * For automatic context creation, the `auto` package needs to be imported (see example).
    * <br/><br/>
    * <i>Note</i>: Up to 5 implicit value can be automatically wrapped; for more values, a dedicated
    * case class might be a more readable/maintainable option.
    * <br/><br/>
    * <strong>Example</strong>:
    *  {{{
    *    import io.github.sndnv.layers.service.components.auto._
    *    // ^ enables support for automatic wrapping and unwrapping of context and component values
    *
    *    implicit val system: ActorSystem[_] = ???
    *    implicit val some: SomeType = ???
    *
    *    implicit val context1: Context[ActorSystem[_]] =
    *      implicitly // implicitly uses `system`
    *
    *    implicit val context2: Context[(ActorSystem[_], SomeType)] =
    *      implicitly // implicitly uses `system` and `some`
    *
    *    implicit val context3: Context[String] =
    *      Context("abc") // explicitly created using a value
    *
    *    // note - if the expected implicits are in scope, a context
    *    //        usually does not have to be manually defined
    *
    *    def someFunction(arg1: String)(implicit Context[OtherType]): Result = ???
    *
    *    implicit val other: OtherType = ???
    *
    *    // an implicit of type Context[OtherType] is automatically created
    *    val result1 = someFunction(arg1 = "abc")
    *
    *    // note - if a component is part of another context, it can be automatically provided
    *    val otherComponent: Component[OtherType] = ???
    *
    *    // will use `otherComponent` if an implicit of `OtherType` is not in scope
    *    val result2 = someFunction(arg1 = "abc")
    *  }}}
    *
    * @param underlying actual context value
    */
  final class Context[V](private val underlying: V) {

    /**
      * Provides the actual context value.
      */
    implicit def value: V = underlying

    /**
      * Creates a new context by adding the provided extra value to the current context value.
      *
      * @param extra value to add to the current one
      * @return
      */
    def withExtraValue[V2](extra: V2): Context[(V, V2)] =
      new Context[(V, V2)](underlying = (underlying, extra))

    /**
      * Maps the current context value to a new one, using the provided function.
      *
      * @param f value mapping function
      * @return a new context with the mapped value
      */
    def map[T](f: V => T): Context[T] =
      new Context[T](underlying = f(underlying))
  }

  /**
    * @see [[Context]]
    */
  object Context {
    def apply[V](value: V): Context[V] = new Context(underlying = value)

    sealed abstract class Empty
    case object Empty extends Empty

    implicit val empty: Context[Empty] = new Context[Empty](underlying = Empty)
  }
}
