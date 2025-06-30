package io.github.sndnv.layers.persistence

import scala.concurrent.Future

import org.apache.pekko.Done

/**
  * Generic key-value data store.
  */
trait KeyValueStore[K, V] extends Store {

  /**
    * Inserts a new `value` or updates an existing one with the specified `key`.
    *
    * @param key key
    * @param value value
    *
    * @return the result of the operation
    */
  def put(key: K, value: V): Future[Done]

  /**
    * Retrieves a value with the specified `key`, if it exists.
    *
    * @param key key
    * @return the value, if it exists
    */
  def get(key: K): Future[Option[V]]

  /**
    * Deletes a value with the specified `key`, if it exists.
    *
    * @param key key
    * @return `true`, if the value was found
    */
  def delete(key: K): Future[Boolean]

  /**
    * Checks if a value with the specified `key` exists.
    *
    * @param key key
    * @return `true`, if the value was found
    */
  def contains(key: K): Future[Boolean]

  /**
    * Retrieves all stored entries.
    *
    * @return all entries
    */
  def entries: Future[Map[K, V]]

  /**
    * Adds all provided entries.
    *
    * Any duplicates with be overwritten using the value from provided `entries`.
    *
    * @param entries entries to add
    * @return the result of the operation
    */
  def load(entries: Map[K, V]): Future[Done]
}
