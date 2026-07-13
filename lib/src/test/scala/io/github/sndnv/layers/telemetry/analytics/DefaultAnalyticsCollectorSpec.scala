package io.github.sndnv.layers.telemetry.analytics

import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

import scala.concurrent.Future
import scala.concurrent.duration.*
import scala.util.Success

import io.github.sndnv.layers.telemetry.ApplicationInformation
import io.github.sndnv.layers.telemetry.mocks.MockAnalyticsPersistence
import io.github.sndnv.layers.testing.UnitSpec
import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.scalatest.concurrent.Eventually

class DefaultAnalyticsCollectorSpec extends UnitSpec with Eventually {
  "A DefaultAnalyticsCollector" should "record events" in withRetry {
    val persistence = MockAnalyticsPersistence()

    val collector = DefaultAnalyticsCollector(
      name = "test-analytics-collector",
      config = config,
      persistence = persistence,
      app = ApplicationInformation.none
    )

    collector.recordEvent("test_event")
    collector.recordEvent("test_event", "a" -> "b")
    collector.recordEvent("test_event", "a" -> "b", "c" -> "d")
    collector.recordEvent("test_event", Map("a" -> "b"))

    collector.state.map { state =>
      state.events.toList match {
        case event1 :: event2 :: event3 :: event4 :: Nil =>
          event1.id should be(0)
          event1.event should be("test_event")

          event2.id should be(1)
          event2.event should be("test_event{a='b'}")

          event3.id should be(2)
          event3.event should be("test_event{a='b',c='d'}")

          event4.id should be(3)
          event4.event should be("test_event{a='b'}")

        case other =>
          fail(s"Unexpected result received: [$other]")
      }

      state.failures should be(empty)
    }
  }

  it should "record failures" in withRetry {
    val persistence = new MockAnalyticsPersistence(existing = Success(None)) {
      override def lastTransmitted: Instant = Instant.now() // prevents transmission
    }

    val collector = DefaultAnalyticsCollector(
      name = "test-analytics-collector",
      config = config,
      persistence = persistence,
      app = ApplicationInformation.none
    )

    collector.recordFailure(e = new RuntimeException("Test failure"))
    collector.recordFailure(message = "Other failure")
    collector.recordFailure(message = "Other failure", stackTrace = Some("abc"))

    collector.state.map { state =>
      state.events should be(empty)

      state.failures.toList match {
        case failure1 :: failure2 :: failure3 :: Nil =>
          failure1.message should be("RuntimeException - Test failure")
          failure1.stackTrace should not be empty

          failure2.message should be("Other failure")
          failure2.stackTrace should be(empty)

          failure3.message should be("Other failure")
          failure3.stackTrace should be(Some("abc"))

        case other =>
          fail(s"Unexpected result received: [$other]")
      }
    }
  }

  it should "support loading cache state" in withRetry {
    val persistence = MockAnalyticsPersistence(
      existing = AnalyticsEntry
        .collected(app = ApplicationInformation.none)
        .withEvent(name = "existing_event", attributes = Map.empty)
        .withFailure(message = "Existing failure")
    )

    val collector = DefaultAnalyticsCollector(
      name = "test-analytics-collector",
      config = config,
      persistence = persistence,
      app = ApplicationInformation.none
    )

    collector.recordEvent("test_event")

    collector.state.map { state =>
      state.events.toList match {
        case event1 :: event2 :: Nil =>
          event1.id should be(0)
          event1.event should be("existing_event")

          event2.id should be(1)
          event2.event should be("test_event")

        case other =>
          fail(s"Unexpected result received: [$other]")
      }

      state.failures.map(_.message) should be(Seq("Existing failure"))

      persistence.cached should be(empty)
      persistence.transmitted should be(empty)
    }
  }

  it should "defer cached state to a pending queue when the app version changes" in withRetry {
    val persistence = MockAnalyticsPersistence(
      existing = AnalyticsEntry
        .collected(app = previousApp)
        .withEvent(name = "existing_event", attributes = Map.empty)
        .withFailure(message = "Existing failure")
    )

    val collector = DefaultAnalyticsCollector(
      name = "test-analytics-collector",
      config = config.copy(persistenceInterval = 1.minute),
      persistence = persistence,
      app = currentApp
    )

    collector.recordEvent("test_event")

    collector.state.map { state =>
      state.runtime.app should be(currentApp.asString())
      state.events.map(_.event) should be(Seq("test_event"))
      state.failures should be(empty)

      persistence.transmitted should be(empty)

      persistence.pending.toList match {
        case pendingEntry :: Nil =>
          pendingEntry.runtime.app should be(previousApp.asString())
          pendingEntry.events.map(_.event) should be(Seq("existing_event"))
          pendingEntry.failures.map(_.message) should be(Seq("Existing failure"))

        case other =>
          fail(s"Unexpected result received: [$other]")
      }

      persistence.cached.toList match {
        case cached :: Nil =>
          cached.runtime.app should be(currentApp.asString())
          cached.events should be(empty)
          cached.failures should be(empty)

        case other =>
          fail(s"Unexpected result received: [$other]")
      }
    }
  }

  it should "transmit pending entries on the next transmission" in withRetry {
    val persistence = MockAnalyticsPersistence(
      existing = AnalyticsEntry
        .collected(app = previousApp)
        .withEvent(name = "existing_event", attributes = Map.empty)
    )

    val collector = DefaultAnalyticsCollector(
      name = "test-analytics-collector",
      config = config,
      persistence = persistence,
      app = currentApp
    )

    collector.recordEvent("test_event")
    collector.send()

    eventually {
      collector.state.map { _ =>
        persistence.pending should be(empty)

        persistence.transmitted.toList match {
          case pendingEntry :: currentEntry :: Nil =>
            pendingEntry.runtime.app should be(previousApp.asString())
            pendingEntry.events.map(_.event) should be(Seq("existing_event"))

            currentEntry.runtime.app should be(currentApp.asString())
            currentEntry.events.map(_.event) should be(Seq("test_event"))

          case other =>
            fail(s"Unexpected result received: [$other]")
        }
      }
    }
  }

  it should "queue multiple pending entries across version changes" in withRetry {
    val persistence = MockAnalyticsPersistence(
      existing = AnalyticsEntry
        .collected(app = previousApp)
        .withEvent(name = "previous_event", attributes = Map.empty)
    )

    persistence.cachePending(
      entries = Seq(
        AnalyticsEntry
          .collected(app = olderApp)
          .withEvent(name = "older_event", attributes = Map.empty)
      )
    )

    val collector = DefaultAnalyticsCollector(
      name = "test-analytics-collector",
      config = config.copy(persistenceInterval = 1.minute),
      persistence = persistence,
      app = currentApp
    )

    collector.state.map { state =>
      state.runtime.app should be(currentApp.asString())

      persistence.pending.toList match {
        case older :: previous :: Nil =>
          older.runtime.app should be(olderApp.asString())
          older.events.map(_.event) should be(Seq("older_event"))

          previous.runtime.app should be(previousApp.asString())
          previous.events.map(_.event) should be(Seq("previous_event"))

        case other =>
          fail(s"Unexpected result received: [$other]")
      }
    }
  }

  it should "retain pending entries when transmission fails" in withRetry {
    val existing = AnalyticsEntry
      .collected(app = previousApp)
      .withEvent(name = "existing_event", attributes = Map.empty)

    val persistence = new MockAnalyticsPersistence(existing = Success(Some(existing))) {
      override def transmit(entry: AnalyticsEntry): Future[Done] =
        Future.failed(new RuntimeException("Test failure"))
    }

    val collector = DefaultAnalyticsCollector(
      name = "test-analytics-collector",
      config = config,
      persistence = persistence,
      app = currentApp
    )

    collector.send()

    eventually {
      collector.state.map { _ =>
        persistence.pending.toList match {
          case pendingEntry :: Nil =>
            pendingEntry.runtime.app should be(previousApp.asString())
            pendingEntry.events.map(_.event) should be(Seq("existing_event"))

          case other =>
            fail(s"Unexpected result received: [$other]")
        }

        persistence.transmitted should be(empty)
      }
    }
  }

  it should "retain only failed pending entries when transmission partially fails" in withRetry {
    val existing = AnalyticsEntry
      .collected(app = previousApp)
      .withEvent(name = "previous_event", attributes = Map.empty)

    val persistence = new MockAnalyticsPersistence(existing = Success(Some(existing))) {
      override def transmit(entry: AnalyticsEntry): Future[Done] =
        if (entry.runtime.app == olderApp.asString()) Future.failed(new RuntimeException("Test failure"))
        else super.transmit(entry)
    }

    persistence.cachePending(
      entries = Seq(
        AnalyticsEntry
          .collected(app = olderApp)
          .withEvent(name = "older_event", attributes = Map.empty)
      )
    )

    val collector = DefaultAnalyticsCollector(
      name = "test-analytics-collector",
      config = config,
      persistence = persistence,
      app = currentApp
    )

    collector.recordEvent("test_event")
    collector.send()

    eventually {
      collector.state.map { _ =>
        persistence.pending.toList match {
          case older :: Nil =>
            older.runtime.app should be(olderApp.asString())
            older.events.map(_.event) should be(Seq("older_event"))

          case other =>
            fail(s"Unexpected result received: [$other]")
        }

        persistence.transmitted.toList match {
          case previous :: current :: Nil =>
            previous.runtime.app should be(previousApp.asString())
            previous.events.map(_.event) should be(Seq("previous_event"))

            current.runtime.app should be(currentApp.asString())
            current.events.map(_.event) should be(Seq("test_event"))

          case other =>
            fail(s"Unexpected result received: [$other]")
        }
      }
    }
  }

  it should "handle unexpected transmission failures for pending entries" in withRetry {
    val existing = AnalyticsEntry
      .collected(app = previousApp)
      .withEvent(name = "existing_event", attributes = Map.empty)

    val persistence = new MockAnalyticsPersistence(existing = Success(Some(existing))) {
      override def transmit(entry: AnalyticsEntry): Future[Done] =
        throw new RuntimeException("Test failure")
    }

    val collector = DefaultAnalyticsCollector(
      name = "test-analytics-collector",
      config = config,
      persistence = persistence,
      app = currentApp
    )

    collector.recordEvent("test_event")
    collector.send()

    eventually {
      collector.state.map { state =>
        state.events.map(_.event) should be(Seq("test_event"))

        persistence.pending.toList match {
          case pendingEntry :: Nil =>
            pendingEntry.runtime.app should be(previousApp.asString())
            pendingEntry.events.map(_.event) should be(Seq("existing_event"))

          case other =>
            fail(s"Unexpected result received: [$other]")
        }

        persistence.transmitted should be(empty)
      }
    }
  }

  it should "handle failures when loading cached state" in withRetry {
    val persistence = MockAnalyticsPersistence(existing = new RuntimeException("Test failure"))

    val collector = DefaultAnalyticsCollector(
      name = "test-analytics-collector",
      config = config,
      persistence = persistence,
      app = ApplicationInformation.none
    )

    collector.state.map { state =>
      state.events should be(empty)
      state.failures should be(empty)

      persistence.cached should be(empty)
      persistence.transmitted should be(empty)
    }
  }

  it should "support caching state locally" in withRetry {
    val persistence = new MockAnalyticsPersistence(existing = Success(None)) {
      override def lastTransmitted: Instant = Instant.now() // prevents transmission
    }

    val collector = DefaultAnalyticsCollector(
      name = "test-analytics-collector",
      config = config.copy(persistenceInterval = 100.millis),
      persistence = persistence,
      app = ApplicationInformation.none
    )

    collector.recordEvent("test_event")
    await(delay = 75.millis)
    collector.recordEvent("test_event", "a" -> "b")
    await(delay = 75.millis)
    collector.recordEvent("test_event", "a" -> "b", "c" -> "d")
    await(delay = 75.millis)
    collector.recordEvent("test_event", Map("a" -> "b"))
    await(delay = 150.millis)
    collector.recordFailure(message = "Test failure")

    collector.state.map { state =>
      state.events.toList match {
        case event1 :: event2 :: event3 :: event4 :: Nil =>
          event1.id should be(0)
          event1.event should be("test_event")

          event2.id should be(1)
          event2.event should be("test_event{a='b'}")

          event3.id should be(2)
          event3.event should be("test_event{a='b',c='d'}")

          event4.id should be(3)
          event4.event should be("test_event{a='b'}")

        case other =>
          fail(s"Unexpected result received: [$other]")
      }

      state.failures.map(_.message) should be(Seq("Test failure"))

      persistence.cached.toList match {
        case firstCached :: secondCached :: failureCached :: Nil =>
          firstCached.events.size should be(2)
          firstCached.failures should be(empty)

          secondCached.events.size should be(4)
          secondCached.failures should be(empty)

          failureCached.events.size should be(4)
          failureCached.failures.map(_.message) should be(Seq("Test failure"))

        case other =>
          fail(s"Unexpected result received: [$other]")
      }

      persistence.transmitted should be(empty)
    }
  }

  it should "support transmitting state remotely" in withRetry {
    val persistence = MockAnalyticsPersistence()

    val collector = DefaultAnalyticsCollector(
      name = "test-analytics-collector",
      config = config,
      persistence = persistence,
      app = ApplicationInformation.none
    )

    collector.recordEvent("test_event")
    collector.recordFailure(message = "Test failure")

    eventually {
      collector.state.map { state =>
        state.events should be(empty)
        state.failures should be(empty)

        persistence.cached.toList match {
          case clearCached :: Nil =>
            clearCached.events should be(empty)
            clearCached.failures should be(empty)

          case other =>
            fail(s"Unexpected result received: [$other]")
        }

        persistence.transmitted.toList match {
          case transmitted :: Nil =>
            transmitted.events.size should be(1)
            transmitted.failures.size should be(1)

          case other =>
            fail(s"Unexpected result received: [$other]")
        }
      }
    }
  }

  it should "cache messages while transmitting" in withRetry {
    val transmissionStarted: AtomicBoolean = new AtomicBoolean(false)

    val persistence = new MockAnalyticsPersistence(existing = Success(None)) {
      override def transmit(entry: AnalyticsEntry): Future[Done] = {
        transmissionStarted.set(true)
        await(delay = 250.millis)
        super.transmit(entry)
      }
    }

    val collector = DefaultAnalyticsCollector(
      name = "test-analytics-collector",
      config = config,
      persistence = persistence,
      app = ApplicationInformation.none
    )

    collector.recordFailure(message = "Test failure")

    eventually {
      transmissionStarted.get() should be(true)
    }

    collector.recordEvent("test_event")
    collector.recordEvent("test_event")
    collector.recordEvent("test_event")
    collector.recordFailure(message = "Test failure")

    eventually {
      collector.state.map { state =>
        state.events.size should be(3)
        state.failures.size should be(1)

        persistence.cached.toList match {
          case clearCached :: failureCached :: Nil =>
            clearCached.events should be(empty)
            clearCached.failures should be(empty)

            failureCached.events.size should be(3)
            failureCached.failures.size should be(1)

          case other =>
            fail(s"Unexpected result received: [$other]")
        }

        persistence.transmitted.toList match {
          case transmitted :: Nil =>
            transmitted.events should be(empty)
            transmitted.failures.size should be(1)

          case other =>
            fail(s"Unexpected result received: [$other]")
        }
      }
    }
  }

  it should "handle transmission failures" in withRetry {
    val persistence = new MockAnalyticsPersistence(existing = Success(None)) {
      override def transmit(entry: AnalyticsEntry): Future[Done] =
        Future.failed(new RuntimeException("Test failure"))
    }

    val collector = DefaultAnalyticsCollector(
      name = "test-analytics-collector",
      config = config,
      persistence = persistence,
      app = ApplicationInformation.none
    )

    collector.recordEvent("test_event")
    collector.recordFailure(message = "Test failure")

    eventually {
      collector.state.map { state =>
        state.events.toList match {
          case event1 :: Nil =>
            event1.id should be(0)
            event1.event should be("test_event")

          case other =>
            fail(s"Unexpected result received: [$other]")
        }

        state.failures.map(_.message) should be(Seq("Test failure"))

        persistence.cached.toList match {
          case pendingCached :: Nil =>
            pendingCached.events.size should be(1)
            pendingCached.failures.size should be(1)

          case other =>
            fail(s"Unexpected result received: [$other]")
        }

        persistence.transmitted should be(empty)
      }
    }
  }

  it should "handle unexpected transmission failures" in withRetry {
    val persistence = new MockAnalyticsPersistence(existing = Success(None)) {
      override def transmit(entry: AnalyticsEntry): Future[Done] =
        throw new RuntimeException("Test failure")
    }

    val collector = DefaultAnalyticsCollector(
      name = "test-analytics-collector",
      config = config,
      persistence = persistence,
      app = ApplicationInformation.none
    )

    collector.recordEvent("test_event")
    collector.recordFailure(message = "Test failure")

    eventually {
      collector.state.map { state =>
        state.events.size should be(1)
        state.failures.size should be(1)

        persistence.cached.toList match {
          case pendingCached :: Nil =>
            pendingCached.events.size should be(1)
            pendingCached.failures.size should be(1)

          case other =>
            fail(s"Unexpected result received: [$other]")
        }

        persistence.transmitted should be(empty)
      }
    }
  }

  it should "cache state during termination" in withRetry {
    val persistence = new MockAnalyticsPersistence(existing = Success(None)) {
      override def lastTransmitted: Instant = Instant.now() // prevents transmission
    }

    val collector = DefaultAnalyticsCollector(
      name = "test-analytics-collector",
      config = config.copy(persistenceInterval = config.persistenceInterval.mul(10L)),
      persistence = persistence,
      app = ApplicationInformation.none
    )

    collector.recordEvent("test_event")

    persistence.cached should be(empty)
    persistence.transmitted should be(empty)

    collector.stop()

    eventually {
      persistence.cached.size should be(1)
      persistence.transmitted should be(empty)
    }
  }

  it should "support transmitting state remotely on demand" in withRetry {
    val persistence = new MockAnalyticsPersistence(existing = Success(None)) {
      override def lastTransmitted: Instant = Instant.now() // prevents transmission
    }

    val collector = DefaultAnalyticsCollector(
      name = "test-analytics-collector",
      config = config.copy(persistenceInterval = 100.millis),
      persistence = persistence,
      app = ApplicationInformation.none
    )

    collector.recordFailure(message = "Test failure")

    val state = collector.state.await
    state.events should be(empty)
    state.failures.map(_.message) should be(Seq("Test failure"))

    persistence.cached.toList match {
      case failureCached :: Nil =>
        failureCached.events should be(empty)
        failureCached.failures.map(_.message) should be(Seq("Test failure"))

      case other =>
        fail(s"Unexpected result received: [$other]")
    }

    persistence.transmitted should be(empty)

    collector.send()

    eventually {
      persistence.cached.toList match {
        case failureCached :: emptyCached :: Nil =>
          failureCached.events should be(empty)
          failureCached.failures.map(_.message) should be(Seq("Test failure"))

          emptyCached.events should be(empty)
          emptyCached.failures should be(empty)

        case other =>
          fail(s"Unexpected result received: [$other]")
      }

      persistence.transmitted.toList match {
        case sent :: Nil =>
          sent.events should be(empty)
          sent.failures.map(_.message) should be(Seq("Test failure"))

        case other =>
          fail(s"Unexpected result received: [$other]")
      }
    }
  }

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 100.milliseconds)

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "DefaultAnalyticsCollectorSpec"
  )

  private val config = DefaultAnalyticsCollector.Config(
    persistenceInterval = 3.seconds,
    transmissionInterval = 10.minutes
  )

  private val olderApp: ApplicationInformation = new ApplicationInformation {
    override val name: String = "test-app"
    override val version: String = "older"
    override val buildTime: Long = 0L
  }

  private val previousApp: ApplicationInformation = new ApplicationInformation {
    override val name: String = "test-app"
    override val version: String = "previous"
    override val buildTime: Long = 0L
  }

  private val currentApp: ApplicationInformation = new ApplicationInformation {
    override val name: String = "test-app"
    override val version: String = "current"
    override val buildTime: Long = 0L
  }
}
