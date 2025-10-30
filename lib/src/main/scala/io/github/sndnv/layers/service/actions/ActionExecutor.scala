package io.github.sndnv.layers.service.actions

import java.time.Instant

import scala.collection.immutable.Queue
import scala.concurrent.Future

/**
  * Generic action executor interface.
  * <br/><br/>
  * The executor is responsible for running schedule-based actions at the appropriate time and
  * event-based actions when the relevant events are seen.
  *
  * @see [[ActionExecutor.NoOp]] for the no-op implementation
  * @see [[ActionExecutor.History]] for information about action execution history
  * @see [[Action]] for general action information and examples on creating actions
  * @see [[ActionTrigger]] for trigger information and configuration
  * @see [[DefaultActionExecutor]] for the default action executor
  */
trait ActionExecutor {

  /**
    * Provides the action history of this executor.
    */
  def history: Future[ActionExecutor.History]
}

/**
  * @see [[ActionExecutor]]
  */
object ActionExecutor {

  /**
    * An [[ActionExecutor]] that does not execute any actions and provides no history.
    */
  object NoOp extends ActionExecutor {
    override def history: Future[History] = Future.successful(History.empty)
  }

  /**
    * History of executed events.
    *
    * @param available events that are still in history
    * @param discarded number of events that have been removed from the history so far
    *
    * @see [[ActionExecutor.History.Entry]] for more information about what is kept in history
    */
  final case class History(
    available: Queue[History.Entry],
    discarded: Long
  ) {

    /**
      * Adds a new event entry to the history.
      *
      * @param entry entry to add
      * @return the updated history
      */
    def withEntry(entry: History.Entry): History =
      copy(available = available.enqueue(entry))

    /**
      * Reduces the size of the history to avoid keeping too many entries in memory.
      * <br/><br/>
      * If the limit has been reached, roughly 30% of the entries will be removed.
      * This is done to avoid having to prune the history after each new update,
      * as soon as the limit is reached for the first time.
      *
      * @param maxSize maximum number of entries that should be kept in the history
      * @return the updated history
      */
    def prune(maxSize: Long): History =
      if (available.length >= maxSize) {
        val pruneSize = (available.length - maxSize) + (maxSize * 0.3).toLong
        val updated = available.drop(pruneSize.toInt)
        copy(
          available = updated,
          discarded = discarded + (available.length - updated.length)
        )
      } else {
        this
      }
  }

  /**
    * @see [[ActionExecutor.History]]
    */
  object History {

    /**
      * History entry for an action that was run.
      *
      * @param action the action that was executed
      * @param trigger the trigger that caused the action to run
      * @param started action start time
      * @param completed action completion time
      * @param successful action result, `true` if successful or `false` if not
      */
    final case class Entry(
      action: String,
      trigger: String,
      started: Instant,
      completed: Instant,
      successful: Boolean
    )

    /**
      * Creates a new, empty, history.
      */
    def empty: History =
      History(available = Queue.empty, discarded = 0)
  }
}
