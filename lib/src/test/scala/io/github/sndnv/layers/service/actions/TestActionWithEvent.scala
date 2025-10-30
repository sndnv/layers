package io.github.sndnv.layers.service.actions

import scala.concurrent.Future

import io.github.sndnv.layers.events.Event

class TestActionWithEvent(successful: Boolean) extends Action.WithEvent {
  override val trigger: String = "test_event"

  override def run(event: Event): Future[Option[Event.Lazy]] =
    if (successful) {
      Future.successful(Some(() => Event(name = "test_action_with_event_complete")))
    } else {
      Future.failed(new RuntimeException("Test failure"))
    }
}

object TestActionWithEvent {
  def apply(successful: Boolean): TestActionWithEvent =
    new TestActionWithEvent(successful)
}
