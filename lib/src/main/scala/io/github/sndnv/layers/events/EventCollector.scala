package io.github.sndnv.layers.events

import scala.util.matching.Regex

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source

/**
  * [[Event]] collection component interface.
  * <br/><br/>
  * Allows for publishing of and subscribing to events.
  * @see [[DefaultEventCollector]] for the default (in memory) event collector
  * @see [[EventCollector.NoOp]] for the no-op implementation
  */
trait EventCollector {

  /**
    * Publishes the provided event.
    *
    * @param event the event to be published
    */
  def publish(event: => Event): Unit

  /**
    * Subscribes to events sent to this collector.
    *
    * @param subscriber subscriber reference
    * @return an events stream source
    */
  def subscribe(subscriber: AnyRef): Source[Event, NotUsed] =
    subscribe(subscriber = subscriber, filter = None)

  /**
    * Subscribes to events sent to this collector with the specified name.
    * <br/><br/>
    * <strong>Note</strong>: Multiple subscriptions from the same `subscriber` are allowed, however,
    * unsubscribing is done per reference so if it is shared, all subscriptions will be stopped/removed.
    *
    * @param subscriber subscriber reference
    * @param eventName event name to use for filtering events
    * @return an events stream source
    */
  def subscribe(subscriber: AnyRef, eventName: String): Source[Event, NotUsed] =
    subscribe(subscriber = subscriber, filter = Some { e: Event => e.name == eventName })

  /**
    * Subscribes to events sent to this collector with names matching the specified regular expression.
    *
    * @param subscriber subscriber reference
    * @param eventName event name regex to use for filtering events
    * @return an events stream source
    */
  def subscribe(subscriber: AnyRef, eventName: Regex): Source[Event, NotUsed] =
    subscribe(subscriber = subscriber, filter = Some { e: Event => eventName.matches(e.name) })

  /**
    * Subscribes to events sent to this collector and applies the provided filtering function.
    *
    * @param subscriber subscriber reference
    * @param filter function for filtering events; return `true` to keep the event or `false` to drop it
    * @return an events stream source
    */
  def subscribe(subscriber: AnyRef, filter: Event => Boolean): Source[Event, NotUsed] =
    subscribe(subscriber = subscriber, filter = Some(filter))

  /**
    * Subscribes to events sent to this collector and, if a `filter` is specified, keeps only
    * the events that match the filter. If no `filter` is specified, all events are kept and
    * provided on the stream.
    *
    * @param subscriber subscriber reference
    * @param filter function for filtering events
    * @return an events stream source
    */
  protected def subscribe(subscriber: AnyRef, filter: Option[Event => Boolean]): Source[Event, NotUsed]

  /**
    * Stops the specified subscriber from receiving events.
    * <br/><br/>
    * <strong>Note</strong>: Any active streams will be stopped.
    *
    * @param subscriber subscriber to remove
    */
  def unsubscribe(subscriber: AnyRef): Unit
}

/**
  * @see [[EventCollector]]
  */
object EventCollector {

  /**
    * An [[EventCollector]] that does not publish any events and provides no subscriptions.
    */
  object NoOp extends EventCollector {
    override def publish(event: => Event): Unit = ()

    override protected def subscribe(
      subscriber: AnyRef,
      filter: Option[Event => Boolean]
    ): Source[Event, NotUsed] = Source.empty

    override def unsubscribe(subscriber: AnyRef): Unit = ()
  }
}
