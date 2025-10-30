package io.github.sndnv.layers.events

import scala.concurrent.Future
import scala.concurrent.duration._

import io.github.sndnv.layers.testing.UnitSpec
import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.stream.QueueOfferResult
import org.apache.pekko.stream.scaladsl.Sink
import org.scalatest.concurrent.Eventually

class DefaultEventCollectorSpec extends UnitSpec with Eventually {
  "A DefaultEventCollector" should "support subscribing to and publishing events" in withRetry {
    import io.github.sndnv.layers.events.DefaultEventCollector._

    val collector = DefaultEventCollector(name = "test-collector", config = config)

    collector.state.await should be(State.empty)

    val result = collector
      .subscribe(subscriber = this)
      .runWith(Sink.seq)

    collector.state.await.subscribers.length should be(1)

    collector.publish(event = Event(name = "test1"))
    collector.publish(event = Event(name = "test2"))
    collector.publish(event = Event(name = "test3"))

    eventually {
      val state = collector.state.await
      state.eventsReceived should be(3)
      state.eventsPublished should be(3)
      state.publishingFailures should be(0)
    }

    collector.unsubscribe(subscriber = this)

    eventually {
      collector.state.await.subscribers should be(empty)
    }

    result.map { events =>
      events.map(_.name) should be(Seq("test1", "test2", "test3"))
    }
  }

  it should "support subscriptions with event filtering" in withRetry {
    import io.github.sndnv.layers.events.DefaultEventCollector._

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

    val collector = DefaultEventCollector(name = "test-collector", config = config)

    collector.state.await should be(State.empty)

    val sub1 = collector.subscribe(this).takeWithin(250.millis).runWith(Sink.seq)
    val sub2 = collector.subscribe(this, eventName = "test-a").takeWithin(250.millis).runWith(Sink.seq)
    val sub3 = collector.subscribe(this, eventName = "test-.*".r).takeWithin(250.millis).runWith(Sink.seq)
    val sub4 = collector.subscribe(this, eventName = "test-a.*".r).takeWithin(250.millis).runWith(Sink.seq)
    val sub5 = collector.subscribe(this, filter = e => e.name.startsWith("other")).takeWithin(250.millis).runWith(Sink.seq)

    events.foreach(e => collector.publish(e))

    for {
      subResult1 <- sub1
      subResult2 <- sub2
      subResult3 <- sub3
      subResult4 <- sub4
      subResult5 <- sub5
    } yield {
      subResult1.length should be(events.length)
      subResult2.length should be(1)
      subResult3.length should be(7)
      subResult4.length should be(3)
      subResult5.length should be(1)
    }
  }

  it should "support a quiet period where subscriptions are allowed but messages are not sent" in withRetry {
    import io.github.sndnv.layers.events.DefaultEventCollector._

    val collector = DefaultEventCollector(name = "test-collector", config = config.copy(quietPeriod = 3.seconds))

    collector.state.await should be(State.empty)

    val result = collector
      .subscribe(subscriber = this)
      .runWith(Sink.seq)

    collector.state.await.subscribers.length should be(1)

    collector.publish(event = Event(name = "test1"))
    collector.publish(event = Event(name = "test2"))
    collector.publish(event = Event(name = "test3"))

    await(delay = 250.millis)

    collector.unsubscribe(subscriber = this)

    eventually {
      collector.state.await.subscribers should be(empty)
    }

    result.map { events =>
      events should be(empty) // unsubscribed before the quiet period was over
    }

    eventually {
      val state = collector.state.await
      state.eventsReceived should be(3)
      state.eventsPublished should be(3)
      state.publishingFailures should be(0)
    }
  }

  it should "support multiple subscriptions from the same subscriber" in withRetry {
    import io.github.sndnv.layers.events.DefaultEventCollector._

    val collector = DefaultEventCollector(name = "test-collector", config = config)

    collector.state.await should be(State.empty)

    val result1 = collector
      .subscribe(subscriber = this)
      .runWith(Sink.seq)

    val result2 = collector
      .subscribe(subscriber = this)
      .runWith(Sink.seq)

    val result3 = collector
      .subscribe(subscriber = this)
      .runWith(Sink.seq)

    collector.state.await.subscribers.length should be(3)

    collector.publish(event = Event(name = "test1"))
    collector.publish(event = Event(name = "test2"))
    collector.publish(event = Event(name = "test3"))

    eventually {
      val state = collector.state.await
      state.eventsReceived should be(3)
      state.eventsPublished should be(3)
      state.publishingFailures should be(0)
    }

    collector.unsubscribe(subscriber = this)

    eventually {
      collector.state.await.subscribers should be(empty)
    }

    for {
      events1 <- result1
      events2 <- result2
      events3 <- result3
    } yield {
      events1.map(_.name) should be(Seq("test1", "test2", "test3"))
      events2.map(_.name) should be(Seq("test1", "test2", "test3"))
      events3.map(_.name) should be(Seq("test1", "test2", "test3"))
    }
  }

  it should "handle failures when publishing events" in withRetry {
    import io.github.sndnv.layers.events.DefaultEventCollector._

    val collector = DefaultEventCollector(name = "test-collector", config = config)

    collector.state.await should be(State.empty)

    val _ = collector
      .subscribe(subscriber = this)
      .runWith(Sink.cancelled)

    collector.state.await.subscribers.length should be(1)

    await(delay = 250.millis)

    collector.publish(event = Event(name = "test1"))
    collector.publish(event = Event(name = "test2"))
    collector.publish(event = Event(name = "test3"))

    eventually {
      val state = collector.state.await
      state.eventsReceived should be(3)
      state.eventsPublished should be(3)
      state.publishingFailures should be(3)
    }
  }

  it should "process offer results" in {
    val event = Event(name = "test")
    val (subscriber, _) = DefaultEventCollector.createSubscriber(subscriber = this, filter = None)(config, typedSystem)
    val failure = new RuntimeException("test failure")
    val process: QueueOfferResult => Future[Done] = DefaultEventCollector.processOfferResult(event, subscriber)

    for {
      enqueued <- process(QueueOfferResult.Enqueued)
      dropped <- process(QueueOfferResult.Dropped).failed
      failed <- process(QueueOfferResult.Failure(failure)).failed
      closed <- process(QueueOfferResult.QueueClosed).failed
    } yield {
      enqueued should be(Done)
      dropped.getMessage should be(
        s"Sending event [name=${event.name},timestamp=${event.timestamp.toString}] to subscriber [${subscriber.ref.getClass.getName}] failed; dropped by stream"
      )
      failed.getMessage should be(
        s"Sending event [name=${event.name},timestamp=${event.timestamp.toString}] to subscriber [${subscriber.ref.getClass.getName}] failed; RuntimeException: ${failure.getMessage}"
      )
      closed.getMessage should be(
        s"Sending event [name=${event.name},timestamp=${event.timestamp.toString}] to subscriber [${subscriber.ref.getClass.getName}] failed; stream closed"
      )
    }
  }

  private val config = DefaultEventCollector.Config(
    subscriberBufferSize = 100,
    quietPeriod = 0.seconds
  )

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "DefaultEventCollectorSpec"
  )

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 100.milliseconds)
}
