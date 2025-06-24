package io.github.sndnv.layers.service

import io.github.sndnv.layers.persistence.migration.MigrationResult
import org.apache.pekko.Done

import scala.concurrent.{ExecutionContext, Future}

trait PersistenceProvider { parent =>
  def migrate(): Future[MigrationResult]
  def init(): Future[Done]
  def drop(): Future[Done]

  def combineWith(other: PersistenceProvider)(implicit ec: ExecutionContext): PersistenceProvider =
    new PersistenceProvider {
      override def migrate(): Future[MigrationResult] =
        for {
          parentResult <- parent.migrate()
          otherResult <- other.migrate()
        } yield {
          parentResult + otherResult
        }

      override def init(): Future[Done] = parent.init().flatMap(_ => other.init())

      override def drop(): Future[Done] = parent.drop().flatMap(_ => other.drop())
    }
}
