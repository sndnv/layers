package io.github.sndnv.layers.persistence

import io.github.sndnv.layers.persistence.migration.Migration
import org.apache.pekko.Done

import scala.concurrent.Future

trait Store {
  def name(): String
  def migrations(): Seq[Migration]
  def init(): Future[Done]
  def drop(): Future[Done]
}
