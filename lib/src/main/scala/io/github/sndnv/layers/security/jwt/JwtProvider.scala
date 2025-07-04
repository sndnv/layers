package io.github.sndnv.layers.security.jwt

import scala.concurrent.Future

trait JwtProvider {
  def provide(scope: String): Future[String]
}
