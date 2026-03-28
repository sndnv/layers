package io.github.sndnv.layers.telemetry.analytics

import java.io.FileNotFoundException
import java.time.Instant

import io.github.sndnv.layers.telemetry.ApplicationInformation
import io.github.sndnv.layers.telemetry.analytics.AnalyticsEntrySpec.TestAnalyticsEntry
import io.github.sndnv.layers.testing.UnitSpec

class AnalyticsEntrySpec extends UnitSpec {
  "An AnalyticsEntry" should "support converting to a collected entry" in {
    val now = Instant.now()

    val testEntry = TestAnalyticsEntry(
      id = "test-id",
      runtime = AnalyticsEntry.RuntimeInformation(app = ApplicationInformation.none),
      events = Seq.empty,
      failures = Seq.empty,
      created = now,
      updated = now
    )

    val collectedEntry = AnalyticsEntry
      .collected(app = ApplicationInformation.none)
      .copy(
        created = now,
        updated = now
      )

    testEntry.asCollected() should be(collectedEntry)

    collectedEntry.asCollected() should be(collectedEntry)
  }

  "A Collected AnalyticsEntry" should "support adding events" in {
    val original = AnalyticsEntry.collected(app = ApplicationInformation.none)

    original.events should be(empty)
    original.failures should be(empty)

    val updated = original
      .withEvent(name = "test_event", attributes = Map.empty)
      .withEvent(name = "test_event", attributes = Map("a" -> "b"))
      .withEvent(name = "test_event", attributes = Map("c" -> "d", "a" -> "b"))

    updated.events should not be empty
    updated.failures should be(empty)

    updated.events.toList match {
      case event1 :: event2 :: event3 :: Nil =>
        event1.id should be(0)
        event1.event should be("test_event")

        event2.id should be(1)
        event2.event should be("test_event{a='b'}")

        event3.id should be(2)
        event3.event should be("test_event{a='b',c='d'}")

      case other =>
        fail(s"Unexpected result received: [$other]")
    }
  }

  it should "support adding failures" in {
    val original = AnalyticsEntry.collected(app = ApplicationInformation.none)

    original.events should be(empty)
    original.failures should be(empty)

    val updated = original
      .withFailure(message = "Test failure #1")
      .withFailure(message = "Test failure #2")
      .withFailure(message = "Test failure #3", stackTrace = Some("abc"))

    updated.events should be(empty)
    updated.failures should not be empty

    updated.failures.toList match {
      case failure1 :: failure2 :: failure3 :: Nil =>
        failure1.message should be("Test failure #1")
        failure1.stackTrace should be(empty)

        failure2.message should be("Test failure #2")
        failure2.stackTrace should be(empty)

        failure3.message should be("Test failure #3")
        failure3.stackTrace should be(Some("abc"))

      case other =>
        fail(s"Unexpected result received: [$other]")
    }
  }

  it should "support discarding all events" in {
    val original = AnalyticsEntry.collected(app = ApplicationInformation.none)

    original.events should be(empty)
    original.failures should be(empty)

    val updated = original
      .withEvent(name = "test_event", attributes = Map.empty)
      .withEvent(name = "test_event", attributes = Map("a" -> "b"))
      .withEvent(name = "test_event", attributes = Map("c" -> "d", "a" -> "b"))

    updated.events should not be empty
    updated.failures should be(empty)

    val discarded = updated.discardEvents()

    discarded.events should be(empty)
    discarded.failures should be(empty)
  }

  it should "support discarding all failures" in {
    val original = AnalyticsEntry.collected(app = ApplicationInformation.none)

    original.events should be(empty)
    original.failures should be(empty)

    val updated = original
      .withFailure(message = "Test failure #1")
      .withFailure(message = "Test failure #2")
      .withFailure(message = "Test failure #3")

    updated.events should be(empty)
    updated.failures should not be empty

    val discarded = updated.discardFailures()

    discarded.events should be(empty)
    discarded.failures should be(empty)
  }

  it should "support providing runtime information (without application information)" in {
    val information = AnalyticsEntry.RuntimeInformation(app = ApplicationInformation.none)

    information.id should not be empty
    information.app should be("none;none;0")
    information.jre should not be empty
    information.os should not be empty
  }

  it should "support providing runtime information (with application information)" in {
    val information = AnalyticsEntry.RuntimeInformation(app = new ApplicationInformation {
      override def name: String = "test-name"
      override def version: String = "test-version"
      override def buildTime: Long = 42L
    })

    information.id should not be empty
    information.app should be("test-name;test-version;42")
    information.jre should not be empty
    information.os should not be empty
  }

  it should "support creating failures with and without a stack trace" in {
    val failureWithStacktrace = AnalyticsEntry.Failure(
      message = "Test failure #1",
      timestamp = Instant.now(),
      stackTrace = Some("abc")
    )

    val failureWithoutStacktrace = AnalyticsEntry.Failure(
      message = "Test failure #2",
      timestamp = Instant.now()
    )

    failureWithStacktrace.message should be("Test failure #1")
    failureWithStacktrace.stackTrace should be(Some("abc"))

    failureWithoutStacktrace.message should be("Test failure #2")
    failureWithoutStacktrace.stackTrace should be(empty)
  }

  it should "support extracting failure stack traces" in {
    val trace = AnalyticsEntry.Failure.extractStackTrace(e = new RuntimeException("Test failure")) match {
      case Some(value) => value
      case None        => fail("Expected a value but none was found")
    }

    trace should include("java.lang.RuntimeException: Test failure")
    trace should include("at io.github.sndnv.layers.telemetry.analytics.AnalyticsEntrySpec")
    trace should include("at org.scalatest.flatspec.AsyncFlatSpecLike")
  }

  it should "support anonymizing failure content (paths)" in {
    AnalyticsEntry.Failure.anonymize(content = "") should be("")

    AnalyticsEntry.Failure.anonymize(content = "Test failure") should be("Test failure")

    AnalyticsEntry.Failure.anonymize(content = "/x/y/z") should be("*CONTENT_REMOVED*")

    AnalyticsEntry.Failure.anonymize(content = "Permission denied: '/a/b/c' [Errno 13]") should be(
      "Permission denied: ' *CONTENT_REMOVED* ' [Errno 13]"
    )

    AnalyticsEntry.Failure.anonymize(content = "Permission denied: /a/b/c [Errno 13]") should be(
      "Permission denied: *CONTENT_REMOVED* [Errno 13]"
    )

    AnalyticsEntry.Failure.anonymize(content = "Access to the path 'C:\\a\\b\\c' is denied") should be(
      "Access to the path ' *CONTENT_REMOVED* ' is denied"
    )

    AnalyticsEntry.Failure.anonymize(content = "Access to the path C:\\a\\b\\c is denied") should be(
      "Access to the path *CONTENT_REMOVED*"
    )

    AnalyticsEntry.Failure.anonymize(content = "java.io.FileNotFoundException: C:\\1\\2\\3") should be(
      "java.io.FileNotFoundException: *CONTENT_REMOVED*"
    )

    AnalyticsEntry.Failure.anonymize(content = "java.io.FileNotFoundException: /x/y/ (No such file or directory)") should be(
      "java.io.FileNotFoundException: *CONTENT_REMOVED* (No such file or directory)"
    )

    AnalyticsEntry.Failure.anonymize(content = "java.io.FileNotFoundException: /x/y/z (No such file or directory)") should be(
      "java.io.FileNotFoundException: *CONTENT_REMOVED* (No such file or directory)"
    )
  }

  it should "support anonymizing failures with and without a stack trace" in {
    val original = AnalyticsEntry.collected(app = ApplicationInformation.none)

    original.events should be(empty)
    original.failures should be(empty)

    val e1 = new FileNotFoundException("/x/y/x")
    val e2 = new FileNotFoundException("C:\\a\\b\\c")

    val updated = original
      .withFailure(
        message = s"${e1.getClass.getSimpleName} - ${e1.getMessage}"
      )
      .withFailure(
        message = s"${e1.getClass.getSimpleName} - ${e1.getMessage}",
        stackTrace = AnalyticsEntry.Failure.extractStackTrace(e1)
      )
      .withFailure(
        message = s"${e2.getClass.getSimpleName} - ${e2.getMessage}"
      )
      .withFailure(
        message = s"${e2.getClass.getSimpleName} - ${e2.getMessage}",
        stackTrace = AnalyticsEntry.Failure.extractStackTrace(e2)
      )

    updated.events should be(empty)
    updated.failures.toList match {
      case failure1 :: failure2 :: failure3 :: failure4 :: Nil =>
        failure1.message should be("FileNotFoundException - *CONTENT_REMOVED*")
        failure1.stackTrace should be(empty)

        failure2.message should be("FileNotFoundException - *CONTENT_REMOVED*")
        failure2.stackTrace should not be empty
        failure2.stackTrace.get should startWith("java.io.FileNotFoundException: *CONTENT_REMOVED*")

        failure3.message should be("FileNotFoundException - *CONTENT_REMOVED*")
        failure3.stackTrace should be(empty)

        failure4.message should be("FileNotFoundException - *CONTENT_REMOVED*")
        failure4.stackTrace should not be empty
        failure4.stackTrace.get should startWith("java.io.FileNotFoundException: *CONTENT_REMOVED*")

      case other =>
        fail(s"Unexpected result received: [$other]")
    }
  }
}

object AnalyticsEntrySpec {
  final case class TestAnalyticsEntry(
    id: String,
    override val runtime: AnalyticsEntry.RuntimeInformation,
    override val events: Seq[AnalyticsEntry.Event],
    override val failures: Seq[AnalyticsEntry.Failure],
    override val created: Instant,
    override val updated: Instant
  ) extends AnalyticsEntry
}
