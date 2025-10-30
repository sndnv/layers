package io.github.sndnv.layers.service.actions

import java.time.Instant

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success

import io.github.sndnv.layers.events.Event
import io.github.sndnv.layers.events.EventCollector
import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.AskPattern._
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.scaladsl.TimerScheduler
import org.apache.pekko.util.Timeout
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
  * Default [[ActionExecutor]] implementation.
  * <br/><br/>
  * <b>Note</b>: Enabling this executor but having no configured actions
  * is considered a mistake and an exception will be thrown.
  *
  * @see [[ActionExecutor]] for interface documentation
  * @see [[ActionExecutor.NoOp]] for the no-op executor implementation
  */
class DefaultActionExecutor private (
  executorRef: ActorRef[DefaultActionExecutor.Message]
)(implicit system: ActorSystem[Nothing], timeout: Timeout)
    extends ActionExecutor {
  override def history: Future[ActionExecutor.History] =
    executorRef ? ((ref: ActorRef[ActionExecutor.History]) => DefaultActionExecutor.GetHistory(ref))

  /**
    * Stops the executor.
    */
  def stop(): Unit =
    executorRef ! DefaultActionExecutor.Stop
}

/**
  * @see [[DefaultActionExecutor]]
  */
object DefaultActionExecutor {

  /**
    * Creates a new executor with the provided configuration.
    *
    * @param config executor configuration
    * @param events event collector, for executing event-based actions
    * @return the new executor
    */
  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def apply(
    config: Config,
    events: EventCollector
  )(implicit system: ActorSystem[Nothing], timeout: Timeout): DefaultActionExecutor = {
    implicit val log: Logger = LoggerFactory.getLogger(classOf[DefaultActionExecutor].getName)

    val (definitionsWithEvents, definitionsWithSchedules) =
      config.definitions
        .foldLeft((Seq.empty[ActionDefinition.WithEvent], Seq.empty[ActionDefinition.WithSchedule])) {
          case ((collectedWithEvent, collectedWithSchedule), definition: ActionDefinition.WithEvent) =>
            (collectedWithEvent :+ definition, collectedWithSchedule)

          case ((collectedWithEvent, collectedWithSchedule), definition: ActionDefinition.WithSchedule) =>
            (collectedWithEvent, collectedWithSchedule :+ definition)
        }

    if (config.definitions.nonEmpty) {
      val executorRef = system.systemActorOf(
        behavior = Behaviors.withTimers[Message] { scheduler =>
          executing(history = ActionExecutor.History.empty)(config, definitionsWithSchedules, scheduler, events, log)
        },
        name = s"action-executor-${java.util.UUID.randomUUID().toString}"
      )

      if (definitionsWithSchedules.nonEmpty) {
        executorRef ! PrepareNextSchedule
      } else {
        log.debug("No actions with schedules configured; no schedule-based actions will be executed")
      }

      if (definitionsWithEvents.nonEmpty) {
        if (events == EventCollector.NoOp) {
          log.warn("A no-op event collector is configured; no event-based actions will be executed")
        } else {
          events
            .subscribe(subscriber = this)
            .mapConcat { event =>
              definitionsWithEvents.filter(_.trigger.name == event.name).map { definition =>
                definition.action -> event
              }
            }
            .throttle(elements = config.throttling.actions, per = config.throttling.per)
            .runForeach { case (action, event) =>
              executorRef ! DefaultActionExecutor.ExecuteActionWithEvent(action, event)
            }
            .monitored()
        }
      } else {
        log.debug("No actions with events configured; no event-based actions will be executed")
      }

      new DefaultActionExecutor(executorRef = executorRef)
    } else {
      throw new IllegalArgumentException("No action definitions provided")
    }
  }

  /**
    * Executor configuration.
    *
    * @param definitions configured action definitions
    * @param throttling action execution throttling configuration
    * @param history action history configuration
    */
  final case class Config(
    definitions: Seq[ActionDefinition],
    throttling: Config.Throttling,
    history: Config.History
  )

  /**
    * @see [[DefaultActionExecutor.Config]]
    */
  object Config {

    /**
      * Action execution throttling config.
      * <br/><br/>
      * Defines the maximum number of actions that can be executed per time interval and
      * allows for limiting the impact of actions on the system, especially if it is under
      * heavy load already.
      *
      * @param actions max number of actions
      * @param per time interval
      */
    final case class Throttling(
      actions: Int,
      per: FiniteDuration
    )

    /**
      * Action history configuration.
      * <br/><br/>
      * Defines the maximum number of historic entries that will be kept in memory.
      *
      * @param maxSize max number of entries to keep
      */
    final case class History(
      maxSize: Long
    )
  }

  private def executing(
    history: ActionExecutor.History
  )(implicit
    config: Config,
    definitionsWithSchedules: Seq[ActionDefinition.WithSchedule],
    scheduler: TimerScheduler[Message],
    events: EventCollector,
    log: Logger
  ): Behavior[Message] =
    Behaviors.receive {
      case (ctx, ExecuteActionWithEvent(action, event)) =>
        import ctx.executionContext
        val self = ctx.self

        val started = Instant.now()

        log.debug("Executing action [{}] with event [{}]", action.name, event.description)

        action
          .run(event)
          .onComplete {
            case Success(createdEvent) =>
              log.debug(
                "Action [{}] with event [{}] executed successfully",
                action.name,
                event.description
              )
              createdEvent.foreach(e => events.publish(event = e.apply()))
              self ! ActionCompleted(action, event, started, successful = true)

            case Failure(e) =>
              log.error(
                "Failed to execute action [{}] with event [{}]: [{} - {}]",
                action.name,
                event.name,
                e.getClass.getSimpleName,
                e.getMessage
              )
              self ! ActionCompleted(action, event, started, successful = false)
          }

        Behaviors.same

      case (ctx, ExecuteActionWithSchedule(action, target)) =>
        import ctx.executionContext
        val self = ctx.self

        val actual = Instant.now()

        log.debug("Executing action [{}] with schedule [target={},actual={}]", action.name, target, actual)

        action
          .run(target = target, actual = actual)
          .onComplete { result =>
            result match {
              case Success(createdEvent) =>
                log.debug(
                  "Action [{}] with schedule [target={},actual={}] executed successfully",
                  action.name,
                  target,
                  actual
                )
                createdEvent.foreach(e => events.publish(event = e.apply()))
                self ! ActionCompleted(action, target, actual, successful = true)

              case Failure(e) =>
                log.error(
                  "Failed to execute action [{}] with schedule [target={},actual={}]: [{} - {}]",
                  action.name,
                  target,
                  actual,
                  e.getClass.getSimpleName,
                  e.getMessage
                )
                self ! ActionCompleted(action, target, actual, successful = false)
            }

            self ! PrepareNextSchedule
          }

        Behaviors.same

      case (_, ActionCompleted(entry)) =>
        executing(
          history = history
            .withEntry(entry)
            .prune(maxSize = config.history.maxSize)
        )

      case (_, PrepareNextSchedule) =>
        val now = Instant.now()

        val (action, next) = definitionsWithSchedules.next(after = now)
        val delay = (next.toEpochMilli - now.toEpochMilli).millis

        log.debug(
          "Scheduling next action [{}] with schedule [target={}] in [{}]",
          action.name,
          next,
          delay.toCoarsest
        )

        scheduler.startSingleTimer(
          key = SchedulerKey,
          msg = ExecuteActionWithSchedule(action = action, target = next),
          delay = delay
        )

        Behaviors.same

      case (_, GetHistory(replyTo)) =>
        replyTo ! history
        Behaviors.same

      case (_, Stop) =>
        scheduler.cancelAll()
        Behaviors.stopped
    }

  private[actions] implicit class ExtendedDefinitionsWithSchedules(definitionsWithSchedules: Seq[ActionDefinition.WithSchedule]) {
    @SuppressWarnings(Array("org.wartremover.warts.Throw"))
    def next(after: Instant): (Action.WithSchedule, Instant) =
      definitionsWithSchedules
        .map { definition =>
          definition.action -> definition.trigger.next(after = after)
        }
        .sortBy(_._2)
        .headOption match {
        case Some(result) => result
        case None         => throw new IllegalArgumentException("No scheduled action definitions found")
      }
  }

  private[actions] implicit class ExtendedEventStreamResult(result: Future[Done]) {
    def monitored()(implicit log: Logger, system: ActorSystem[Nothing]): Unit = {
      import system.executionContext

      result
        .onComplete {
          case Success(_) =>
            log.debug("Event stream completed successfully")

          case Failure(e) =>
            log.error("Event stream failed: [{} - {}]", e.getClass.getSimpleName, e.getMessage)
        }
    }
  }

  private sealed trait Message
  private final case class ExecuteActionWithEvent(action: Action.WithEvent, event: Event) extends Message
  private final case class ExecuteActionWithSchedule(action: Action.WithSchedule, target: Instant) extends Message
  private final case object PrepareNextSchedule extends Message
  private final case class GetHistory(replyTo: ActorRef[ActionExecutor.History]) extends Message
  private final case object Stop extends Message

  private final case class ActionCompleted(entry: ActionExecutor.History.Entry) extends Message
  private object ActionCompleted {
    def apply(action: Action, event: Event, started: Instant, successful: Boolean): ActionCompleted =
      ActionCompleted(
        entry = ActionExecutor.History.Entry(
          action = action.name,
          trigger = s"event=${event.name}",
          started = started,
          completed = Instant.now(),
          successful = successful
        )
      )

    def apply(action: Action, target: Instant, started: Instant, successful: Boolean): ActionCompleted =
      ActionCompleted(
        entry = ActionExecutor.History.Entry(
          action = action.name,
          trigger = s"schedule=${target.toString}",
          started = started,
          completed = Instant.now(),
          successful = successful
        )
      )
  }

  private final object SchedulerKey
}
