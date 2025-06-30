package io.github.sndnv.layers.persistence.migration

import scala.concurrent.Future

import io.github.sndnv.layers.persistence.Store
import io.github.sndnv.layers.testing.UnitSpec
import io.github.sndnv.layers.testing.persistence.TestSlickDatabase
import org.apache.pekko.Done
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class MigrationSpec extends UnitSpec with TestSlickDatabase {

  "A Migration" should "run its action" in withRetry {
    withStore { (profile, h2db) =>
      import profile.api._

      val queryRows = sql"""SELECT COUNT(*) FROM TEST_TABLE""".as[Int].head

      val migration = Migration(
        version = 1,
        needed = Migration.Action {
          h2db.run(queryRows.map(_ == 0))
        },
        action = Migration.Action {
          h2db.run(sqlu"""INSERT INTO TEST_TABLE VALUES('abc', 1)""")
        }
      )

      for {
        _ <- h2db.run(sqlu"""CREATE TABLE TEST_TABLE(a varchar not  null, b int not null)""")
        rowsBefore <- h2db.run(queryRows)
        neededBefore <- migration.needed.run()
        migrationRun <- migration.run(forStore = store)
        neededAfter <- migration.needed.run()
        rowsAfter <- h2db.run(queryRows)
        migrationRerun <- migration.run(forStore = store)
        neededAfterRerun <- migration.needed.run()
        rowsAfterRerun <- h2db.run(queryRows)
      } yield {
        rowsBefore should be(0)
        neededBefore should be(true)
        migrationRun should be(true)
        neededAfter should be(false)
        rowsAfter should be(1)
        migrationRerun should be(false)
        neededAfterRerun should be(false)
        rowsAfterRerun should be(1)
      }
    }
  }

  it should "handle failures" in withRetry {
    withStore { (profile, h2db) =>
      import profile.api._

      val migration = Migration(
        version = 1,
        needed = Migration.Action { h2db.run(sql"""SELECT COUNT(*) FROM OTHER_TABLE""".as[Int].head.map(_ == 0)) },
        action = Migration.Action { h2db.run(sqlu"""INSERT INTO OTHER_TABLE VALUES('abc', 1)""") }
      )

      migration.run(forStore = store).failed.map { e =>
        e.getMessage should include("Table \"OTHER_TABLE\" not found")
      }
    }
  }

  private val store: Store = new MigrationSpec.TestStore

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getSimpleName)
}

object MigrationSpec {
  class TestStore extends Store {
    override val name: String = "test-store"
    override val migrations: Seq[Migration] = Seq.empty
    override def init(): Future[Done] = Future.successful(Done)
    override def drop(): Future[Done] = Future.successful(Done)
  }
}
