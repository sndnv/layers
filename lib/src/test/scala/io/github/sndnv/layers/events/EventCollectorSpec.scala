package io.github.sndnv.layers.events

import io.github.sndnv.layers.testing.UnitSpec
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source

class EventCollectorSpec extends UnitSpec {
  "An EventCollector" should "support subscriptions with event filtering" in {
    val events = List(
      Event(name = "test-a"),
      Event(name = "test-a-1"),
      Event(name = "test-a-2"),
      Event(name = "test-b"),
      Event(name = "test-c"),
      Event(name = "test-d"),
      Event(name = "test-e"),
      Event(name = "other")
    )

    val collector: EventCollector = new EventCollector {
      override def publish(event: => Event): Unit = ()

      override protected def subscribe(
        subscriber: AnyRef,
        filter: Option[Event => Boolean]
      ): Source[Event, NotUsed] =
        Source(events).filter(e => filter.forall(_.apply(e)))

      override def unsubscribe(self: AnyRef): Unit = ()
    }

    collector.subscribe(this).runWith(Sink.seq).await.length should be(events.length)

    collector.subscribe(this, eventName = "test-a").runWith(Sink.seq).await.length should be(1)
    collector.subscribe(this, eventName = "test-.*".r).runWith(Sink.seq).await.length should be(7)
    collector.subscribe(this, eventName = "test-a.*".r).runWith(Sink.seq).await.length should be(3)
    collector.subscribe(this, filter = e => e.name.startsWith("other")).runWith(Sink.seq).await.length should be(1)
  }

  "A NoOp EventCollector" should "publish no events and provide no subscriptions" in {
    val collector = EventCollector.NoOp

    noException should be thrownBy collector.publish(event = null)

    collector.subscribe(subscriber = null).runWith(Sink.seq).await should be(empty)
    collector.subscribe(subscriber = null, eventName = "test").runWith(Sink.seq).await should be(empty)
    collector.subscribe(subscriber = null, eventName = "test".r).runWith(Sink.seq).await should be(empty)
    collector.subscribe(subscriber = null, filter = _ => true).runWith(Sink.seq).await should be(empty)

    noException should be thrownBy collector.unsubscribe(subscriber = null)
  }

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "EventCollectorSpec"
  )
}
