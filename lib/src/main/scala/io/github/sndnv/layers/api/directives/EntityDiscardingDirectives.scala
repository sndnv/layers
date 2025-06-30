package io.github.sndnv.layers.api.directives

import io.github.sndnv.layers.streaming.Operators.ExtendedSource
import org.apache.pekko.http.scaladsl.server.Directive
import org.apache.pekko.http.scaladsl.server.Directive0
import org.apache.pekko.http.scaladsl.server.Directives._

trait EntityDiscardingDirectives {

  /**
    *  Discards the request entity without consuming it.
    */
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

  /**
    * Discards the request entity after consuming it.
    */
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
