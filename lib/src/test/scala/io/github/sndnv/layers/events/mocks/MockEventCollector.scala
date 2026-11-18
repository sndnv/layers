package io.github.sndnv.layers.events.mocks

import scala.collection.mutable

import io.github.sndnv.layers.events.DefaultEventCollector
import io.github.sndnv.layers.events.Event
import io.github.sndnv.layers.events.EventCollector
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.stream.scaladsl.Source

class MockEventCollector()(implicit system: ActorSystem[Nothing]) extends EventCollector {
  private val collectedEvents: mutable.Queue[Event] = mutable.Queue.empty

  private val subscribers: mutable.Map[AnyRef, DefaultEventCollector.Subscriber] = mutable.Map.empty

  override def publish(event: => Event): Unit = {
    collectedEvents.append(event)
    subscribers.values.foreach(_.queue.offer(event))
  }

  override protected def subscribe(
    subscriber: AnyRef,
    filter: Option[Event => Boolean]
  ): Source[Event, NotUsed] = {
    val (queue, source) = Source.queue[Event](bufferSize = 100).preMaterialize()
    subscribers.put(subscriber, DefaultEventCollector.Subscriber(ref = subscriber, filter = filter, queue = queue))

    filter match {
      case Some(f) => source.filter(f)
      case None    => source
    }
  }

  override def unsubscribe(subscriber: AnyRef): Unit = {
    val _ = subscribers.remove(subscriber)
  }

  def events: Seq[Event] = collectedEvents.toSeq
}

object MockEventCollector {
  def apply()(implicit system: ActorSystem[Nothing]): MockEventCollector =
    new MockEventCollector()
}
