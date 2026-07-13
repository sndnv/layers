package io.github.sndnv.layers.telemetry.analytics

import java.time.Instant

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.*
import scala.util.Failure
import scala.util.Success

import io.github.sndnv.layers.telemetry.ApplicationInformation
import org.apache.pekko.actor.typed.*
import org.apache.pekko.actor.typed.scaladsl.AskPattern.*
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.scaladsl.TimerScheduler
import org.apache.pekko.util.Timeout

class DefaultAnalyticsCollector private (
  storeRef: ActorRef[DefaultAnalyticsCollector.Message],
  override val persistence: Option[AnalyticsPersistence]
)(implicit scheduler: Scheduler, timeout: Timeout)
    extends AnalyticsCollector {
  override def recordEvent(name: String, attributes: Map[String, String]): Unit =
    storeRef ! DefaultAnalyticsCollector.RecordEvent(name, attributes = attributes)

  override def recordFailure(message: String, stackTrace: Option[String]): Unit =
    storeRef ! DefaultAnalyticsCollector.RecordFailure(message = message, stackTrace = stackTrace)

  override def state: Future[AnalyticsEntry] =
    storeRef ? ((ref: ActorRef[AnalyticsEntry]) => DefaultAnalyticsCollector.GetState(ref))

  override def send(): Unit =
    storeRef ! DefaultAnalyticsCollector.Send

  def stop(): Unit =
    storeRef ! DefaultAnalyticsCollector.Stop
}

object DefaultAnalyticsCollector {
  def apply(
    name: String,
    config: Config,
    persistence: AnalyticsPersistence,
    app: ApplicationInformation
  )(implicit system: ActorSystem[Nothing], timeout: Timeout): DefaultAnalyticsCollector = {
    val storeRef = system.systemActorOf(
      behavior = restoring()(config, persistence, app),
      name = s"$name-${java.util.UUID.randomUUID().toString}"
    )

    storeRef ! LoadState

    new DefaultAnalyticsCollector(storeRef = storeRef, persistence = Some(persistence))
  }

  final case class Config(
    persistenceInterval: FiniteDuration,
    transmissionInterval: FiniteDuration
  )

  private def restoring()(implicit
    config: Config,
    persistence: AnalyticsPersistence,
    app: ApplicationInformation
  ): Behavior[Message] =
    Behaviors.withStash(capacity = Int.MaxValue) { buffer =>
      Behaviors.withTimers { implicit scheduler =>
        Behaviors.receive {
          case (ctx, LoadState) =>
            implicit val ec: ExecutionContext = ctx.executionContext
            ctx.pipeToSelf(
              persistence.restorePending().flatMap { restoredPending =>
                persistence.restore().map {
                  case Some(entry) if entry.runtime.app != app.asString() =>
                    val fresh = AnalyticsEntry.collected(app)
                    val pending = restoredPending :+ entry
                    persistence.cache(entry = fresh)
                    persistence.cachePending(entries = pending)
                    (fresh, pending)

                  case Some(entry) =>
                    (entry.asCollected(), restoredPending)

                  case None =>
                    (AnalyticsEntry.collected(app), restoredPending)
                }
              }
            ) {
              case Success((entry, pending)) =>
                ctx.log.debug(
                  "Analytics state successfully loaded with [events={},failures={},pending={}]",
                  entry.events.length,
                  entry.failures.length,
                  pending.length
                )

                StateLoaded(entry = entry, pending = pending)

              case Failure(e) =>
                ctx.log.error(
                  "Failed to load analytics state: [{} - {}]",
                  e.getClass.getSimpleName,
                  e.getMessage
                )

                StateLoaded(entry = AnalyticsEntry.collected(app), pending = Seq.empty)
            }
            Behaviors.same

          case (_, StateLoaded(entry, pending)) =>
            buffer.unstashAll(collecting(entry, pending))

          case (_, other) =>
            val _ = buffer.stash(other)
            Behaviors.same
        }
      }
    }

  private def collecting(
    entry: AnalyticsEntry.Collected,
    pending: Seq[AnalyticsEntry]
  )(implicit
    config: Config,
    persistence: AnalyticsPersistence,
    app: ApplicationInformation,
    scheduler: TimerScheduler[Message]
  ): Behavior[Message] =
    Behaviors
      .receivePartial[Message] {
        case (_, RecordEvent(name, attributes)) =>
          if (!scheduler.isTimerActive(PersistStateTimerKey)) {
            scheduler.startSingleTimer(PersistStateTimerKey, PersistState(forceTransmit = false), config.persistenceInterval)
          }

          collecting(entry = entry.withEvent(name = name, attributes = attributes), pending = pending)

        case (ctx, RecordFailure(message, stackTrace)) =>
          scheduler.cancel(PersistStateTimerKey)
          ctx.self ! PersistState(forceTransmit = false)

          collecting(entry = entry.withFailure(message = message, stackTrace = stackTrace), pending = pending)

        case (ctx, PersistState(forceTransmit)) =>
          if (
            forceTransmit || persistence.lastTransmitted.plusMillis(config.transmissionInterval.toMillis).isBefore(Instant.now())
          ) {
            implicit val ec: ExecutionContext = ctx.executionContext
            ctx.pipeToSelf(
              Future.delegate {
                transmitPending(pending).flatMap { remaining =>
                  persistence
                    .transmit(entry)
                    .map(_ => (remaining, Option.empty[Throwable]))
                    .recover { case e => (remaining, Some(e)) }
                }
              }
            ) {
              case Success((remaining, None)) =>
                ctx.log.debug(
                  "Analytics state successfully transmitted with [events={},failures={},pending={}]",
                  entry.events.length,
                  entry.failures.length,
                  remaining.length
                )

                StateTransmitted(pending = remaining, successful = true)

              case Success((remaining, Some(e))) =>
                ctx.log.error(
                  "Failed to transmit analytics state with [events={},failures={}]: [{} - {}]",
                  entry.events.length,
                  entry.failures.length,
                  e.getClass.getSimpleName,
                  e.getMessage
                )

                StateTransmitted(pending = remaining, successful = false)

              case Failure(e) =>
                ctx.log.error(
                  "Failed to transmit analytics state: [{} - {}]",
                  e.getClass.getSimpleName,
                  e.getMessage
                )

                StateTransmitted(pending = pending, successful = false)
            }
            transmitting(inFlight = entry)
          } else {
            persistence.cache(entry = entry)
            Behaviors.same
          }

        case (_, GetState(replyTo)) =>
          replyTo.tell(entry)
          Behaviors.same

        case (ctx, Send) =>
          ctx.self ! PersistState(forceTransmit = true)
          Behaviors.same

        case (_, Stop) =>
          Behaviors.stopped
      }
      .receiveSignal { case (_, PostStop) =>
        scheduler.cancel(PersistStateTimerKey)
        persistence.cache(entry = entry)
        Behaviors.same
      }

  private def transmitPending(
    pending: Seq[AnalyticsEntry]
  )(implicit persistence: AnalyticsPersistence, ec: ExecutionContext): Future[Seq[AnalyticsEntry]] =
    if (pending.isEmpty) {
      Future.successful(pending)
    } else {
      Future
        .foldLeft(
          pending.map { entry =>
            persistence.transmit(entry).map(_ => Seq.empty[AnalyticsEntry]).recover { case _ => Seq(entry) }
          }
        )(Seq.empty[AnalyticsEntry])(_ ++ _)
        .map { remaining =>
          if (remaining.size != pending.size) persistence.cachePending(entries = remaining)
          remaining
        }
    }

  private def transmitting(
    inFlight: AnalyticsEntry.Collected
  )(implicit
    config: Config,
    persistence: AnalyticsPersistence,
    app: ApplicationInformation,
    scheduler: TimerScheduler[Message]
  ): Behavior[Message] =
    Behaviors.withStash(capacity = Int.MaxValue) { buffer =>
      Behaviors.receiveMessage {
        case StateTransmitted(pending, true) =>
          val empty = AnalyticsEntry.collected(app)
          persistence.cache(entry = empty)
          buffer.unstashAll(collecting(entry = empty, pending = pending))

        case StateTransmitted(pending, false) =>
          persistence.cache(entry = inFlight)
          buffer.unstashAll(collecting(entry = inFlight, pending = pending))

        case other =>
          val _ = buffer.stash(other)
          Behaviors.same
      }
    }

  private sealed trait Message
  private final case class RecordEvent(name: String, attributes: Map[String, String]) extends Message
  private final case class RecordFailure(message: String, stackTrace: Option[String]) extends Message
  private final case class GetState(replyTo: ActorRef[AnalyticsEntry]) extends Message
  private case object Send extends Message
  private final case class PersistState(forceTransmit: Boolean) extends Message
  private case object LoadState extends Message
  private final case class StateLoaded(entry: AnalyticsEntry.Collected, pending: Seq[AnalyticsEntry]) extends Message
  private final case class StateTransmitted(pending: Seq[AnalyticsEntry], successful: Boolean) extends Message
  private case object Stop extends Message

  private object PersistStateTimerKey
}
