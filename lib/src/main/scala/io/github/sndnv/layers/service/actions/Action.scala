package io.github.sndnv.layers.service.actions

import java.time.Instant

import scala.concurrent.Future

import io.github.sndnv.layers.events.Event

/**
  * Generic action interface.
  * <br/><br/>
  * Actions are meant to represent work that is to be executed in response to an event or on a schedule.
  *
  * @see [[Action.WithSchedule]] for schedule-based actions
  * @see [[Action.WithEvent]] for event-based actions
  * @see [[ActionTrigger]] for trigger information and configuration
  * @see [[ActionExecutor]] for information about the component that manages the actions
  */
sealed trait Action {

  /**
    * Action name.
    * <br/><br/>
    * By default, the action's class name is used.
    */
  def name: String = getClass.getSimpleName
}

/**
  * @see [[Action]]
  */
object Action {

  /**
    * Action that is executed based on a schedule.
    * <br/><br/>
    * <b>Example</b>:
    * {{{
    *   class TestAction extends Action.WithSchedule {
    *     override def run(target: Instant, actual: Instant): Future[Option[Event.Lazy]] = {
    *       val result = Future { ... } // do work
    *
    *       val emitEvent = ??? // decide if an event should be emitted
    *
    *       result.map { _ =>
    *         if (emitEvent) Some(() => Event(..))
    *         else None
    *       }
    *     }
    *   }
    * }}}
    *
    * @see [[Action.WithEvent]] for event-based actions
    */
  trait WithSchedule extends Action {

    /**
      * Runs the action with the specified parameters.
      *
      * @param target timestamp of when the action <i>should</i> have executed
      * @param actual timestamp of when then action <i>actually</i> executed
      * @return an event describing the action that was executed (if any)
      */
    def run(target: Instant, actual: Instant): Future[Option[Event.Lazy]]
  }

  /**
    * Action that is executed based on an event.
    * <br/><br/>
    * <b>Example</b>:
    * {{{
    *   class TestAction extends Action.WithEvent {
    *     override val trigger: String = "test_event"
    *
    *     override def run(event: Event): Future[Option[Event.Lazy]] = {
    *       val result = Future { ... } // do work; potentially using/based on event attributes
    *
    *       val emitEvent = ??? // decide if an event should be emitted
    *
    *       result.map { _ =>
    *         if (emitEvent) Some(() => Event(..))
    *         else None
    *       }
    *     }
    *   }
    * }}}
    *
    * @see [[Action.WithSchedule]] for schedule-based actions
    */
  trait WithEvent extends Action {

    /**
      * Name of the event that is expected to trigger this action.
      */
    def trigger: String

    /**
      * Runs the action with the specified parameters.
      *
      * @param event the event that triggered this action
      * @return an event describing the action that was executed (if any)
      */
    def run(event: Event): Future[Option[Event.Lazy]]
  }
}
