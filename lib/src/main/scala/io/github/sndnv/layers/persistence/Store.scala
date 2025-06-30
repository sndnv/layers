package io.github.sndnv.layers.persistence

import scala.concurrent.Future

import io.github.sndnv.layers.persistence.migration.Migration
import org.apache.pekko.Done

/**
  * Generic data store with support for life-cycle management and migrations.
  */
trait Store {

  /**
    * Data store name.
    */
  def name(): String

  /**
    * List of data store migrations.
    */
  def migrations(): Seq[Migration]

  /**
    * Initializes the data store.
    *
    * Warnings: The semantics and safety of this operation are defined by the implementer.
    *
    * @return the result of the operation
    */
  def init(): Future[Done]

  /**
    * Destroys or resets the data store.
    *
    * Warnings: The semantics and safety of this operation are defined by the implementer.
    *
    * @return the result of the operation
    */
  def drop(): Future[Done]
}
