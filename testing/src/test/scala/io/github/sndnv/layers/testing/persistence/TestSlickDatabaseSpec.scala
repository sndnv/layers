package io.github.sndnv.layers.testing.persistence

import scala.util.Failure
import scala.util.Success
import scala.util.Using

import io.github.sndnv.layers.testing.UnitSpec
import slick.jdbc.H2Profile
import slick.jdbc.JdbcBackend
import slick.jdbc.JdbcProfile

class TestSlickDatabaseSpec extends UnitSpec with TestSlickDatabase {
  "A TestSlickDatabase" should "support providing a test database" in {
    withStore { (profile: JdbcProfile, db: JdbcBackend#JdbcDatabaseDef) =>
      profile should be(H2Profile)

      Using(db.source.createConnection()) { connection =>
        connection.getMetaData.getDatabaseProductName
      } match {
        case Success(databaseProductName) =>
          databaseProductName should be("H2")

        case Failure(e) =>
          fail(s"Expected a valid result but [${e.getClass.getSimpleName} - ${e.getMessage}] encountered")
      }
    }
  }
}
