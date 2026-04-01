package io.github.sndnv.layers.testing.persistence

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future

import slick.jdbc.H2Profile

trait TestSlickDatabase {
  def withStore[T](f: (H2Profile, H2Profile.backend.Database) => Future[T]): Future[T] =
    withStore(name = getClass.getSimpleName)(f)

  def withStore[T](name: String)(f: (H2Profile, H2Profile.backend.Database) => Future[T]): Future[T] =
    withStoreFromUrl(url = s"jdbc:h2:mem:$name")(f = f)

  def withStore[T](mode: TestSlickDatabase.Mode)(f: (H2Profile, H2Profile.backend.Database) => Future[T]): Future[T] =
    withStore(name = getClass.getSimpleName, mode = mode)(f)

  def withStore[T](name: String, mode: TestSlickDatabase.Mode)(
    f: (H2Profile, H2Profile.backend.Database) => Future[T]
  ): Future[T] =
    withStoreFromUrl(url = s"jdbc:h2:mem:$name;${mode.settings}")(f = f)

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def withStoreFromUrl[T](url: String)(f: (H2Profile, H2Profile.backend.Database) => Future[T]): Future[T] = {
    val (actualUrl, providedSettings) = url.trim.split(";").filter(_.nonEmpty).toList match {
      case first :: Nil   => (first, "")
      case first :: other => (first, other.mkString(";"))
      case Nil            => throw new IllegalArgumentException(s"Unexpected URL provided: [$url]")
    }

    val existingSettings = TestSlickDatabase.initialUrlSettings.getOrElseUpdate(actualUrl, providedSettings)

    require(
      existingSettings == providedSettings,
      s"Cannot change settings for an existing database [$actualUrl]; existing=[$existingSettings], provided=[$providedSettings]"
    )

    val database = H2Profile.api.Database.forURL(url = url, keepAliveConnection = true)

    import scala.concurrent.ExecutionContext.Implicits.global
    val result = f(H2Profile, database)
    result.onComplete(_ => database.close())
    result
  }
}

object TestSlickDatabase {
  sealed trait Mode {
    def settings: String
  }

  object Mode {
    case object Strict extends Mode {
      override val settings: String = "MODE=STRICT"
    }

    case object Legacy extends Mode {
      override val settings: String = "MODE=LEGACY"
    }

    case object PostgreSQL extends Mode {
      override val settings: String = "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
    }

    case object MariaDB extends Mode {
      override val settings: String = "MODE=MariaDB;DATABASE_TO_LOWER=TRUE"
    }

    final case class Custom(override val settings: String) extends Mode
  }

  private val initialUrlSettings: TrieMap[String, String] =
    new TrieMap()
}
