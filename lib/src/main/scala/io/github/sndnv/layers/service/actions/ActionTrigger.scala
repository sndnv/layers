package io.github.sndnv.layers.service.actions

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

import scala.annotation.tailrec
import scala.concurrent.duration._

import com.typesafe.config.Config

/**
  * A trigger determines when an action is to be executed - either on a schedule or as
  * a response to an event.
  *
  * @see [[ActionTrigger.Schedule]] for schedule-based action triggers
  * @see [[ActionTrigger.Event]] for event-based action triggers
  * @see [[Action]] for general action information and examples on creating actions
  * @see [[ActionExecutor]] for information about the component that manages the actions
  */
sealed trait ActionTrigger {

  /**
    * Human-readable description of the trigger.
    * <br/><br/>
    * <i>Usually used for logging purposes.</i>
    */
  def description: String
}

/**
  * @see [[ActionTrigger]]
  */
object ActionTrigger {

  /**
    * Schedule-based trigger defining when and how frequently to execute an action.
    *
    * @param start trigger start time
    * @param interval trigger interval
    *
    * @see [[ActionTrigger.Schedule.next]] for determining when is the next schedule invocation
    */
  final case class Schedule(start: LocalTime, interval: FiniteDuration) extends ActionTrigger {
    override lazy val description: String =
      s"schedule-start=${start.toString},schedule-interval=${interval.toCoarsest.toString}"

    /**
      * Calculates the next execution timestamp.
      *
      * @param after limits the calculated timestamp to be after this date/time
      * @return the next execution timestamp
      */
    def next(after: Instant): Instant = {
      val intervalAmount = interval.toMillis

      val zone = ZoneOffset.UTC
      val zonedAfter = after.atZone(zone)
      val zonedStart = zonedAfter
        .withHour(start.getHour)
        .withMinute(start.getMinute)
        .withSecond(start.getSecond)

      @tailrec
      def nextInstant(last: ZonedDateTime): ZonedDateTime =
        if (last.isAfter(zonedAfter)) {
          last
        } else {
          nextInstant(last = last.plus(intervalAmount, ChronoUnit.MILLIS))
        }

      nextInstant(last = zonedStart).toInstant
    }
  }

  /**
    * @see [[ActionTrigger.Schedule]]
    */
  object Schedule {

    /**
      * Creates a new schedule-based trigger with the provided configuration.
      * <br/><br/>
      * <b>Example</b>:
      * {{{
      *   {
      *     start = "12:34:56"
      *     interval = 6 h
      *   }
      * }}}
      *
      * @param config trigger configuration
      * @return the newly created trigger
      */
    def apply(config: Config): Schedule =
      Schedule(
        start = LocalTime.parse(config.getString("start")),
        interval = config.getDuration("interval").toMillis.millis
      )
  }

  /**
    * Event-based trigger defining which event would cause the execution of an action.
    * <br/><br/>
    * By default, the event that will trigger an action is defined by the action itself,
    * and it is not configurable.
    *
    * @param name event name
    */
  final case class Event(name: String) extends ActionTrigger {
    override lazy val description: String = s"event-name=$name"
  }
}
