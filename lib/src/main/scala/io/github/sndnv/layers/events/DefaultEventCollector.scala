package io.github.sndnv.layers.events

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.Failure
import scala.util.Success

import io.github.sndnv.layers.events.exceptions.EventCollectionFailure
import org.apache.pekko.Done
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed._
import org.apache.pekko.actor.typed.scaladsl.AskPattern._
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.scaladsl.StashBuffer
import org.apache.pekko.stream.OverflowStrategy
import org.apache.pekko.stream.QueueOfferResult
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.stream.scaladsl.SourceQueueWithComplete
import org.apache.pekko.util.Timeout
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
  * Default [[EventCollector]] implementation.
  * <br/><br/>
  * Events are only ever present in memory and are discarded after all relevant
  * subscribers have received them. There's no support for replaying, providing
  * past events, or keeping track of progress per subscriber.
  *
  * @see [[EventCollector]] for interface documentation
  * @see [[EventCollector.NoOp]] for the no-op collector implementation
  */
class DefaultEventCollector private (
  collectorRef: ActorRef[DefaultEventCollector.Message]
)(implicit scheduler: Scheduler, timeout: Timeout)
    extends EventCollector {
  override def publish(event: => Event): Unit =
    collectorRef ! DefaultEventCollector.Publish(event = event)

  override protected def subscribe(subscriber: AnyRef, filter: Option[Event => Boolean]): Source[Event, NotUsed] =
    Source
      .futureSource(
        collectorRef ? ((ref: ActorRef[Source[Event, NotUsed]]) =>
          DefaultEventCollector.Subscribe(subscriber = subscriber, filter = filter, replyTo = ref)
        )
      )
      .mapMaterializedValue(_ => NotUsed)

  override def unsubscribe(subscriber: AnyRef): Unit =
    collectorRef ! DefaultEventCollector.Unsubscribe(subscriber = subscriber)

  private[events] def state: Future[DefaultEventCollector.State] =
    collectorRef ? ((ref: ActorRef[DefaultEventCollector.State]) => DefaultEventCollector.GetState(ref))
}

/**
  * @see [[DefaultEventCollector]]
  */
object DefaultEventCollector {

  /**
    * Creates a new collector with the provided name and configuration.
    *
    * @param name collector name
    * @param config collector configuration
    * @return the new collector
    *
    * @see [[EventCollector]] for interface documentation
    * @see [[DefaultEventCollector]] for this collector's documnetation
    * @see [[DefaultEventCollector.Config]] for this collector's configuration documnetation
    */
  def apply(
    name: String,
    config: Config
  )(implicit system: ActorSystem[Nothing], timeout: Timeout): DefaultEventCollector = {
    implicit val log: Logger = LoggerFactory.getLogger(classOf[DefaultEventCollector].getName)

    val collectorRef = system.systemActorOf(
      behavior = Behaviors.withTimers[Message] { scheduler =>
        Behaviors.withStash(capacity = Int.MaxValue) { buffer =>
          scheduler.startSingleTimer(StartCollection, config.quietPeriod)
          subscribing(state = State.empty)(config, buffer, log)
        }
      },
      name = s"$name-${java.util.UUID.randomUUID().toString}"
    )

    new DefaultEventCollector(collectorRef = collectorRef)
  }

  /**
    * Collector configuration.
    *
    * @param subscriberBufferSize number of events to buffer when the subscriber's stream is not fast enough;
    *                             if the buffer is full when a new event arrives, the oldest event is dropped
    *                             to make space for the new one
    * @param quietPeriod amount of time to wait before dispatching events (after initial collector start);
    *                    subscriptions are allowed during this period and events will be buffered but no
    *                    subscriber will receive any event; buffered events are sent in the order they were received
    */
  final case class Config(
    subscriberBufferSize: Int,
    quietPeriod: FiniteDuration
  )

  private def subscribing(state: State)(implicit config: Config, buffer: StashBuffer[Message], log: Logger): Behavior[Message] =
    Behaviors.receive {
      case (ctx, Subscribe(subscriber, filter, replyTo)) =>
        import ctx.system

        val (created, source) = createSubscriber(subscriber, filter)

        replyTo ! source

        log.debug(
          "Adding subscriber [{}] to [{}] existing subscriber(s)",
          subscriber.getClass.getName,
          state.subscribers.length
        )

        subscribing(state = state.addSubscriber(created))

      case (_, Unsubscribe(subscriber)) =>
        log.debug(
          "Removing subscriber [{}] from [{}] existing subscriber(s)",
          subscriber.getClass.getName,
          state.subscribers.length
        )

        subscribing(state = state.removeSubscriber(subscriber))

      case (_, StartCollection) =>
        log.debug(
          "Starting collection with [{}] stashed message(s)",
          buffer.size
        )

        buffer.unstashAll(collecting(state = state))

      case (_, GetState(replyTo)) =>
        replyTo ! state
        Behaviors.same

      case (_, other) =>
        val _ = buffer.stash(other)
        Behaviors.same
    }

  private def collecting(state: State)(implicit config: Config, log: Logger): Behavior[Message] =
    Behaviors.receivePartial {
      case (ctx, Publish(event)) =>
        import ctx.executionContext
        val self = ctx.self

        if (state.subscribers.isEmpty) {
          collecting(state = state.eventReceivedAndPublished())
        } else {
          Future
            .sequence(
              state.subscribers.collect {
                case sub if sub.matches(event) =>
                  sub
                    .send(event)
                    .transform {
                      case Success(_) => Success(true) /* successful */
                      case Failure(e) =>
                        log.error(
                          "Failed to publish event [name={},timestamp={}]: [{} - {}]",
                          event.name,
                          event.timestamp,
                          e.getClass.getSimpleName,
                          e.getMessage
                        )

                        Success(false) /* failed */
                    }(ExecutionContext.parasitic)
              }
            )
            .foreach { results =>
              self ! PublishComplete(failures = results.count(result => !result))
            }

          publishing(state = state.eventReceived())
        }

      case (ctx, Subscribe(subscriber, filter, replyTo)) =>
        import ctx.system

        val (created, source) = createSubscriber(subscriber, filter)

        replyTo ! source

        log.debug(
          "Adding subscriber [{}] to [{}] existing subscriber(s); latest events: [received={},published={}]",
          subscriber.getClass.getName,
          state.subscribers.length,
          state.eventsReceived,
          state.eventsPublished
        )

        collecting(state = state.addSubscriber(created)(log))

      case (_, Unsubscribe(subscriber)) =>
        log.debug(
          "Removing subscriber [{}] from [{}] existing subscriber(s); latest events: [received={},published={}]",
          subscriber.getClass.getName,
          state.subscribers.length,
          state.eventsReceived,
          state.eventsPublished
        )

        collecting(state = state.removeSubscriber(subscriber)(log))

      case (_, GetState(replyTo)) =>
        replyTo ! state
        Behaviors.same
    }

  private def publishing(state: State)(implicit config: Config, log: Logger): Behavior[Message] =
    Behaviors.withStash(capacity = Int.MaxValue) { buffer =>
      Behaviors.receiveMessage {
        case PublishComplete(failures) =>
          buffer.unstashAll(collecting(state = state.eventPublished(withFailures = failures)))

        case GetState(replyTo) =>
          replyTo ! state
          Behaviors.same

        case other =>
          val _ = buffer.stash(other)
          Behaviors.same
      }
    }

  private[events] final case class State(
    eventsReceived: Long,
    eventsPublished: Long,
    publishingFailures: Long,
    subscribers: Seq[Subscriber]
  ) {
    def eventReceivedAndPublished(): State =
      copy(eventsReceived = eventsReceived + 1, eventsPublished = eventsPublished + 1)

    def eventReceived(): State =
      copy(eventsReceived = eventsReceived + 1)

    def eventPublished(withFailures: Int): State =
      copy(eventsPublished = eventsPublished + 1, publishingFailures = publishingFailures + withFailures)

    def addSubscriber(subscriber: Subscriber)(implicit log: Logger): State = {
      val existingSubscriptions = subscribers.count(_.ref == subscriber.ref)
      if (existingSubscriptions > 0) {
        log.debug(
          "Subscriber [{}] already has [{}] existing subscription(s)",
          subscriber.ref.getClass.getName,
          existingSubscriptions
        )
      }

      copy(subscribers = subscribers :+ subscriber)
    }

    def removeSubscriber(subscriber: AnyRef)(implicit log: Logger): State = {
      val (removedSubscribers, remainingSubscribers) = subscribers.partition(_.ref == subscriber)

      removedSubscribers.foreach { sub =>
        sub.queue.complete()
      }

      val existingSubscriptions = removedSubscribers.length
      if (existingSubscriptions != 1) {
        log.debug(
          "[{}] existing subscriptions found and removed for subscriber [{}]",
          existingSubscriptions,
          subscriber.getClass.getName
        )
      }

      copy(subscribers = remainingSubscribers)
    }
  }

  private[events] object State {
    def empty: State =
      State(
        eventsReceived = 0,
        eventsPublished = 0,
        publishingFailures = 0,
        subscribers = Seq.empty
      )
  }

  private sealed trait Message
  private final case class Publish(event: Event) extends Message

  private final case class Subscribe(
    subscriber: AnyRef,
    filter: Option[Event => Boolean],
    replyTo: ActorRef[Source[Event, NotUsed]]
  ) extends Message

  private final case class Unsubscribe(subscriber: AnyRef) extends Message

  private final case class PublishComplete(failures: Int) extends Message

  private final case object StartCollection extends Message

  private final case class GetState(replyTo: ActorRef[State]) extends Message

  private[events] final case class Subscriber(
    ref: AnyRef,
    filter: Option[Event => Boolean],
    queue: SourceQueueWithComplete[Event]
  ) {
    def matches(event: Event): Boolean =
      filter.forall(f => f(event))

    def send(event: Event): Future[Done] =
      queue
        .offer(event)
        .flatMap(processOfferResult(event, this))(ExecutionContext.parasitic)
  }

  private[events] def createSubscriber(
    subscriber: AnyRef,
    filter: Option[Event => Boolean]
  )(implicit config: Config, system: ActorSystem[Nothing]): (Subscriber, Source[Event, NotUsed]) = {
    val (queue, source) = Source
      .queue[Event](
        bufferSize = 0,
        overflowStrategy = OverflowStrategy.dropHead,
        maxConcurrentOffers = config.subscriberBufferSize
      )
      .preMaterialize()

    val sub = Subscriber(
      ref = subscriber,
      filter = filter,
      queue = queue
    )

    (sub, source)
  }

  private[events] def processOfferResult(event: Event, subscriber: Subscriber)(
    result: QueueOfferResult
  ): Future[Done] = {
    def collectionFailure(cause: String): Future[Done] =
      Future.failed(
        EventCollectionFailure(
          message = s"Sending event [name=${event.name},timestamp=${event.timestamp.toString}] " +
            s"to subscriber [${subscriber.ref.getClass.getName}] failed; $cause"
        )
      )

    result match {
      case QueueOfferResult.Enqueued    => Future.successful(Done)
      case QueueOfferResult.Dropped     => collectionFailure(cause = "dropped by stream")
      case QueueOfferResult.Failure(e)  => collectionFailure(cause = s"${e.getClass.getSimpleName}: ${e.getMessage}")
      case QueueOfferResult.QueueClosed => collectionFailure(cause = "stream closed")
    }
  }
}
