package io.github.sndnv.layers.service.actions

import java.time.Instant

import scala.concurrent.Future

import io.github.sndnv.layers.events.Event

class TestActionWithSchedule(successful: Boolean) extends Action.WithSchedule {
  override def run(target: Instant, actual: Instant): Future[Option[Event.Lazy]] =
    if (successful) {
      Future.successful(Some(() => Event(name = "test_action_with_schedule_complete")))
    } else {
      Future.failed(new RuntimeException("Test failure"))
    }
}

object TestActionWithSchedule {
  def apply(successful: Boolean): TestActionWithSchedule =
    new TestActionWithSchedule(successful)
}
