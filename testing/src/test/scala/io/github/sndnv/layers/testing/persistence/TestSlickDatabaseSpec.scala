package io.github.sndnv.layers.testing.persistence

import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Using

import io.github.sndnv.layers.testing.UnitSpec
import slick.jdbc.H2Profile

class TestSlickDatabaseSpec extends UnitSpec with TestSlickDatabase {
  "A TestSlickDatabase" should "support providing a test database" in {
    withStore { (profile, db) =>
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

  it should "support providing a test database with a specific mode" in {
    withClue("mode=Strict") {
      val mode = TestSlickDatabase.Mode.Strict
      mode.settings should be("MODE=STRICT")

      withStore(name = "TestSlickDatabaseSpec__mode_strict", mode = mode) { (profile, db) =>
        modeOf(profile, db) should be("STRICT")
      }
    }

    withClue("mode=Legacy") {
      val mode = TestSlickDatabase.Mode.Legacy
      mode.settings should be("MODE=LEGACY")

      withStore(name = "TestSlickDatabaseSpec__mode_legacy", mode = mode) { (profile, db) =>
        modeOf(profile, db) should be("LEGACY")
      }
    }

    withClue("mode=PostgreSQL") {
      val mode = TestSlickDatabase.Mode.PostgreSQL
      mode.settings should be("MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")

      withStore(name = "TestSlickDatabaseSpec__mode_postgresql", mode = mode) { (profile, db) =>
        modeOf(profile, db) should be("PostgreSQL")
      }
    }

    withClue("mode=MariaDB") {
      val mode = TestSlickDatabase.Mode.MariaDB
      mode.settings should be("MODE=MariaDB;DATABASE_TO_LOWER=TRUE")

      withStore(name = "TestSlickDatabaseSpec__mode_mariadb", mode = mode) { (profile, db) =>
        modeOf(profile, db) should be("MariaDB")
      }
    }

    withClue("mode=Custom") {
      val mode = TestSlickDatabase.Mode.Custom(settings = "MODE=DB2")
      mode.settings should be("MODE=DB2")

      withStore(name = "TestSlickDatabaseSpec__mode_custom", mode = mode) { (profile, db) =>
        modeOf(profile, db) should be("DB2")
      }
    }
  }

  it should "fail if a different mode is selected for a database that was already created" in {
    // default mode for this class' store
    withStore { (profile, db) =>
      modeOf(profile, db) should be("REGULAR")
    }

    // another mode for this class' store
    val e = intercept[IllegalArgumentException] {
      withStore(mode = TestSlickDatabase.Mode.Legacy) { (_, _) => Future.successful(()) }
    }

    e.getMessage should include(
      "Cannot change settings for an existing database [jdbc:h2:mem:TestSlickDatabaseSpec]; " +
        "existing=[], provided=[MODE=LEGACY]"
    )
  }

  it should "fail if an empty URL is provided" in {
    val e = intercept[IllegalArgumentException] {
      withStoreFromUrl(url = "") { (_, _) => Future.successful(()) }
    }

    e.getMessage should include("Unexpected URL provided: []")
  }

  private def modeOf(profile: H2Profile, db: H2Profile.backend.Database): String = {
    import profile.api.*
    db.run(sql"""SELECT SETTING_VALUE FROM INFORMATION_SCHEMA.SETTINGS WHERE SETTING_NAME='MODE'""".as[String]).await.head
  }
}
