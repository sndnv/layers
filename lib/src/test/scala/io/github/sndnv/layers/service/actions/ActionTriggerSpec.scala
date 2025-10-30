package io.github.sndnv.layers.service.actions

import java.time.Instant
import java.time.LocalTime

import scala.concurrent.duration._

import io.github.sndnv.layers.testing.UnitSpec

class ActionTriggerSpec extends UnitSpec {
  "A Schedule ActionTrigger" should "load itself from config" in {
    val expected = ActionTrigger.Schedule(
      start = LocalTime.of(12, 34, 56),
      interval = 6.hours
    )

    val actual = ActionTrigger.Schedule(config = config.getConfig("schedule"))

    actual should be(expected)
  }

  it should "provide its description" in {
    val trigger = ActionTrigger.Schedule(
      start = LocalTime.of(12, 34, 56),
      interval = 6.hours
    )

    trigger.description should be("schedule-start=12:34:56,schedule-interval=6 hours")
  }

  it should "provie its next execution time" in {
    val trigger = ActionTrigger.Schedule(
      start = LocalTime.of(12, 34, 56),
      interval = 5.hours
    )

    val instants = Map(
      Instant.parse("2020-01-01T00:00:00Z") -> Instant.parse("2020-01-01T12:34:56Z"),
      Instant.parse("2020-01-01T06:00:00Z") -> Instant.parse("2020-01-01T12:34:56Z"),
      Instant.parse("2020-01-01T12:00:00Z") -> Instant.parse("2020-01-01T12:34:56Z"),
      Instant.parse("2020-01-01T13:00:00Z") -> Instant.parse("2020-01-01T17:34:56Z"),
      Instant.parse("2020-01-01T17:00:00Z") -> Instant.parse("2020-01-01T17:34:56Z"),
      Instant.parse("2020-01-01T18:00:00Z") -> Instant.parse("2020-01-01T22:34:56Z"),
      Instant.parse("2020-01-01T23:59:59Z") -> Instant.parse("2020-01-02T03:34:56Z"),
      Instant.parse("2020-01-02T03:34:56Z") -> Instant.parse("2020-01-02T12:34:56Z")
    )

    instants.foreach { case (after, expected) =>
      withClue(s"With instant [$after]") {
        trigger.next(after = after) should be(expected)
      }
    }

    succeed
  }

  "An Event ActionTrigger" should "provide its description" in {
    val trigger = ActionTrigger.Event(name = "test-event")

    trigger.description should be("event-name=test-event")
  }

  private val config = com.typesafe.config.ConfigFactory
    .load()
    .getConfig("io.github.sndnv.layers.test.service.actions.triggers")
}
