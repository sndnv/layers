package io.github.sndnv.layers.testing.persistence

import scala.concurrent.Future

import slick.jdbc.H2Profile

trait TestSlickDatabase {
  def withStore[T](name: String)(f: (H2Profile, H2Profile.backend.Database) => Future[T]): Future[T] = {
    val database = H2Profile.api.Database.forURL(url = s"jdbc:h2:mem:$name", keepAliveConnection = true)

    import scala.concurrent.ExecutionContext.Implicits.global
    val result = f(H2Profile, database)
    result.onComplete(_ => database.close())
    result
  }

  def withStore[T](f: (H2Profile, H2Profile#Backend#Database) => Future[T]): Future[T] =
    withStore(name = getClass.getSimpleName)(f)
}
