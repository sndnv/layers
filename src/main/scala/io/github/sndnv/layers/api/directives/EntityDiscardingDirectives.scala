package io.github.sndnv.layers.api.directives

import io.github.sndnv.layers.streaming.Operators.ExtendedSource
import org.apache.pekko.http.scaladsl.server.{Directive, Directive0}
import org.apache.pekko.http.scaladsl.server.Directives._

trait EntityDiscardingDirectives {
  def discardEntity: Directive0 =
    Directive { inner =>
      extractRequestEntity { entity =>
        extractActorSystem { implicit system =>
          onSuccess(entity.dataBytes.cancelled()) { _ =>
            inner(())
          }
        }
      }
    }

  def consumeEntity: Directive0 =
    Directive { inner =>
      extractRequestEntity { entity =>
        extractActorSystem { implicit system =>
          onSuccess(entity.dataBytes.ignored()) { _ =>
            inner(())
          }
        }
      }
    }
}
