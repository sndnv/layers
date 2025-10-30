package io.github.sndnv.layers.service.components.internal

import scala.reflect.ClassTag
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import io.github.sndnv.layers.service.components.Component

@SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
object DynamicComponentClassLoader {
  def load[T](
    componentClassName: String,
    componentConfig: com.typesafe.config.Config
  )(implicit tag: ClassTag[T]): Try[Component[T]] =
    for {
      cls <- loadComponentClass[T](className = componentClassName)(tag)
      instance <- createComponentInstance[T](cls = cls, config = componentConfig)
    } yield {
      Try(instance.getClass.getDeclaredMethod("renderConfig", classOf[String])) match {
        case Success(renderConfigMethod) if renderConfigMethod.getReturnType.equals(classOf[String]) =>
          new Component[T] {
            override val component: T = instance

            override def renderConfig(withPrefix: String): String =
              renderConfigMethod.invoke(instance, withPrefix).asInstanceOf[String]
          }

        case _ =>
          Component.withoutConfig(comp = instance)
      }
    }

  private def loadComponentClass[T](className: String)(implicit tag: ClassTag[T]): Try[Class[T]] =
    Try(Class.forName(className)) match {
      case Success(cls) =>
        if (tag.runtimeClass.isAssignableFrom(cls)) {
          Success(cls.asInstanceOf[Class[T]])
        } else {
          Failure(
            new IllegalArgumentException(
              s"Target component [${cls.getName}] does not conform to expected type [${tag.runtimeClass.getName}]"
            )
          )
        }

      case Failure(e) =>
        Failure(
          new IllegalArgumentException(
            s"Failed to find component from [$className]: " +
              s"[${e.getClass.getSimpleName} - ${e.getMessage}]"
          )
        )
    }

  private def createComponentInstance[T](cls: Class[T], config: com.typesafe.config.Config): Try[T] =
    Try(cls.getDeclaredConstructor(classOf[com.typesafe.config.Config]))
      .flatMap { constructor =>
        Try(constructor.newInstance(config)).recoverWith { case e =>
          Failure(
            new IllegalArgumentException(
              s"Failed to create component instance from config-based constructor of [${cls.getName}]: " +
                s"[${e.getClass.getSimpleName} - ${e.getMessage}]"
            )
          )
        }
      }
      .recoverWith { case _: NoSuchMethodException =>
        Try(cls.getDeclaredConstructors.find(_.getParameterCount == 0))
          .flatMap {
            case Some(constructor) =>
              Success(constructor)

            case None =>
              Failure(
                new NoSuchMethodException(
                  s"${cls.getName}.<init>(com.typesafe.config.Config) or ${cls.getName}.<init>()"
                )
              )
          } match {
          case Success(constructor) =>
            Try(constructor.newInstance().asInstanceOf[T]).recoverWith { case e =>
              Failure(
                new IllegalArgumentException(
                  s"Failed to create component instance from no-arguments constructor of [${cls.getName}]: " +
                    s"[${e.getClass.getSimpleName} - ${e.getMessage}]"
                )
              )
            }

          case Failure(e) =>
            Failure(
              new IllegalArgumentException(
                s"Failed to get a supported component constructor from [${cls.getName}]: " +
                  s"[${e.getClass.getSimpleName} - ${e.getMessage}]"
              )
            )
        }
      }
}
