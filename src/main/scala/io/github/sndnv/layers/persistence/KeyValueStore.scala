package io.github.sndnv.layers.persistence

import org.apache.pekko.Done

import scala.concurrent.Future

trait KeyValueStore[K, V] extends Store {
  def put(key: K, value: V): Future[Done]
  def get(key: K): Future[Option[V]]
  def delete(key: K): Future[Boolean]
  def contains(key: K): Future[Boolean]
  def entries: Future[Map[K, V]]
  def load(entries: Map[K, V]): Future[Done]
}
