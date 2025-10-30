package io.github.sndnv.layers.events.mocks

import scala.collection.mutable
import scala.concurrent.duration._

import io.github.sndnv.layers.events.DefaultEventCollector
import io.github.sndnv.layers.events.Event
import io.github.sndnv.layers.events.EventCollector
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.stream.scaladsl.Source

class MockEventCollector()(implicit system: ActorSystem[Nothing]) extends EventCollector {
  private val collectedEvents: mutable.Queue[Event] = mutable.Queue.empty

  private val subscribers: mutable.Map[AnyRef, DefaultEventCollector.Subscriber] = mutable.Map.empty

  private implicit val config: DefaultEventCollector.Config = DefaultEventCollector.Config(
    subscriberBufferSize = 100,
    quietPeriod = 0.seconds
  )

  override def publish(event: => Event): Unit = {
    collectedEvents.append(event)
    subscribers.values.foreach(_.queue.offer(event))
  }

  override protected def subscribe(
    subscriber: AnyRef,
    filter: Option[Event => Boolean]
  ): Source[Event, NotUsed] = {
    val (sub, source) = DefaultEventCollector.createSubscriber(subscriber = subscriber, filter = filter)
    subscribers.put(subscriber, sub)

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
