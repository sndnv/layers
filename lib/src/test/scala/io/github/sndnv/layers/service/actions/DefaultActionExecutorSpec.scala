package io.github.sndnv.layers.service.actions

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset

import scala.concurrent.Future
import scala.concurrent.duration._

import io.github.sndnv.layers.events.Event
import io.github.sndnv.layers.events.EventCollector
import io.github.sndnv.layers.events.mocks.MockEventCollector
import io.github.sndnv.layers.testing.UnitSpec
import org.apache.pekko.Done
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.Timeout
import org.mockito.scalatest.AsyncMockitoSugar
import org.scalatest.concurrent.Eventually
import org.slf4j.Logger

class DefaultActionExecutorSpec extends UnitSpec with Eventually with AsyncMockitoSugar {
  "A DefaultActionExecutor" should "provide next scheduled action" in withRetry {
    import DefaultActionExecutor.ExtendedDefinitionsWithSchedules

    val now = Instant.now()

    val action = TestActionWithSchedule(successful = true)

    val definitions: Seq[ActionDefinition.WithSchedule] = Seq(
      ActionDefinition.WithSchedule(
        action = action,
        trigger = ActionTrigger.Schedule(
          start = LocalTime.from(now.atZone(ZoneOffset.UTC)),
          interval = 10.seconds
        )
      ),
      ActionDefinition.WithSchedule(
        action = action,
        trigger = ActionTrigger.Schedule(
          start = LocalTime.from(now.atZone(ZoneOffset.UTC)),
          interval = 5.seconds
        )
      ),
      ActionDefinition.WithSchedule(
        action = action,
        trigger = ActionTrigger.Schedule(
          start = LocalTime.from(now.atZone(ZoneOffset.UTC)),
          interval = 30.seconds
        )
      )
    )

    definitions.next(after = now) should be((action, now.plusSeconds(5)))
  }

  it should "fail to provide next scheduled action if no actions are available" in withRetry {
    import DefaultActionExecutor.ExtendedDefinitionsWithSchedules

    val definitions: Seq[ActionDefinition.WithSchedule] = Seq.empty

    an[IllegalArgumentException] should be thrownBy definitions.next(after = Instant.now())
  }

  it should "execute actions (event-based)" in withRetry {
    val collector = new MockEventCollector {
      override protected def subscribe(subscriber: AnyRef, filter: Option[Event => Boolean]): Source[Event, NotUsed] =
        Source(
          Seq(
            Event(name = "test_event")
          )
        )
    }

    val executor = DefaultActionExecutor(
      config = config.copy(definitions =
        Seq(
          ActionDefinition.WithEvent(
            action = TestActionWithEvent(successful = true),
            trigger = ActionTrigger.Event(name = "test_event")
          )
        )
      ),
      events = collector
    )

    eventually {
      val history = executor.history.await

      history.available.toList match {
        case entry :: Nil =>
          entry.action should be("TestActionWithEvent")
          entry.trigger should be("event=test_event")
          entry.successful should be(true)

        case other =>
          fail(s"Unexpected result received: [$other]")
      }

      history.discarded should be(0)
    }

    collector.events.toList match {
      case createdEvent :: Nil =>
        createdEvent.name should be("test_action_with_event_complete")
        createdEvent.attributes should be(empty)

      case other =>
        fail(s"Unexpected result received: [$other]")
    }

    executor.stop()
    succeed
  }

  it should "execute actions (schedule-based)" in withRetry {
    val now = Instant.now()

    val collector = MockEventCollector()

    val executor = DefaultActionExecutor(
      config = config.copy(definitions =
        Seq(
          ActionDefinition.WithSchedule(
            action = TestActionWithSchedule(successful = true),
            trigger = ActionTrigger.Schedule(
              start = LocalTime.from(now.atZone(ZoneOffset.UTC)),
              interval = 500.millis
            )
          )
        )
      ),
      events = collector
    )

    await(delay = 750.millis)

    val history = executor.history.await

    history.available should not be empty

    val entry = history.available.head
    entry.action should be("TestActionWithSchedule")
    entry.trigger should startWith("schedule=")
    entry.successful should be(true)

    collector.events.toList match {
      case createdEvent :: Nil =>
        createdEvent.name should be("test_action_with_schedule_complete")
        createdEvent.attributes should be(empty)

      case other =>
        fail(s"Unexpected result received: [$other]")
    }

    executor.stop()
    succeed
  }

  it should "handle action failures (event-based)" in withRetry {
    val collector = new MockEventCollector {
      override protected def subscribe(subscriber: AnyRef, filter: Option[Event => Boolean]): Source[Event, NotUsed] =
        Source(
          Seq(
            Event(name = "test_event")
          )
        )
    }

    val executor = DefaultActionExecutor(
      config = config.copy(definitions =
        Seq(
          ActionDefinition.WithEvent(
            action = TestActionWithEvent(successful = false),
            trigger = ActionTrigger.Event(name = "test_event")
          )
        )
      ),
      events = collector
    )

    eventually {
      val history = executor.history.await

      history.available.toList match {
        case entry :: Nil =>
          entry.action should be("TestActionWithEvent")
          entry.trigger should be("event=test_event")
          entry.successful should be(false)

        case other =>
          fail(s"Unexpected result received: [$other]")
      }

      history.discarded should be(0)
    }

    collector.events should be(empty)

    executor.stop()
    succeed
  }

  it should "handle action failures (schedule-based)" in withRetry {
    val now = Instant.now()

    val executor = DefaultActionExecutor(
      config = config.copy(definitions =
        Seq(
          ActionDefinition.WithSchedule(
            action = TestActionWithSchedule(successful = false),
            trigger = ActionTrigger.Schedule(
              start = LocalTime.from(now.atZone(ZoneOffset.UTC)),
              interval = 500.millis
            )
          )
        )
      ),
      events = EventCollector.NoOp
    )

    await(delay = 750.millis)

    val history = executor.history.await

    history.available should not be empty

    val entry = history.available.head
    entry.action should be("TestActionWithSchedule")
    entry.trigger should startWith("schedule=")
    entry.successful should be(false)

    executor.stop()
    succeed
  }

  it should "monitor event stream completion (successful)" in withRetry {
    import DefaultActionExecutor.ExtendedEventStreamResult

    implicit val logger: Logger = mock[Logger]

    Future
      .successful(Done)
      .monitored()

    await(100.millis)

    verify(logger).debug(
      "Event stream completed successfully"
    )

    succeed
  }

  it should "monitor event stream completion (failed)" in withRetry {
    import DefaultActionExecutor.ExtendedEventStreamResult

    implicit val logger: Logger = mock[Logger]

    Future
      .failed(new RuntimeException("Test failure"))
      .monitored()

    await(100.millis)

    verify(logger).error(
      "Event stream failed: [{} - {}]",
      "RuntimeException",
      "Test failure"
    )

    succeed
  }

  it should "not process events if no event-based action definitions are provided" in withRetry {
    val collector = new MockEventCollector {
      override protected def subscribe(subscriber: AnyRef, filter: Option[Event => Boolean]): Source[Event, NotUsed] =
        Source(
          Seq(
            Event(name = "test_event")
          )
        )
    }

    val executor = DefaultActionExecutor(
      config = config.copy(definitions =
        Seq(
          ActionDefinition.WithSchedule(
            action = TestActionWithSchedule(successful = true),
            trigger = ActionTrigger.Schedule(
              start = LocalTime.from(Instant.now().atZone(ZoneOffset.UTC)),
              interval = 30.seconds
            )
          )
        )
      ),
      events = collector
    )

    await(delay = 500.millis)

    val history = executor.history.await
    history.available should be(empty)
    history.discarded should be(0)

    executor.stop()
    succeed
  }

  it should "not process events if a no-op event collector is provided" in withRetry {
    val executor = DefaultActionExecutor(
      config = config.copy(definitions =
        Seq(
          ActionDefinition.WithEvent(
            action = TestActionWithEvent(successful = true),
            trigger = ActionTrigger.Event(name = "test_event")
          )
        )
      ),
      events = EventCollector.NoOp
    )

    await(delay = 500.millis)

    val history = executor.history.await
    history.available should be(empty)
    history.discarded should be(0)

    executor.stop()
    succeed
  }

  it should "fail if no action definitions are provided" in withRetry {
    an[IllegalArgumentException] should be thrownBy DefaultActionExecutor(
      config = config.copy(definitions = Seq.empty),
      events = EventCollector.NoOp
    )
  }

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "DefaultActionExecutorSpec"
  )

  private val config = DefaultActionExecutor.Config(
    definitions = Seq.empty,
    throttling = DefaultActionExecutor.Config.Throttling(
      actions = 100,
      per = 1.second
    ),
    history = DefaultActionExecutor.Config.History(
      maxSize = 3
    )
  )

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 100.milliseconds)

  override implicit val timeout: Timeout = 5.seconds
}
