package io.github.sndnv.layers.service.actions

import java.time.Instant

import io.github.sndnv.layers.testing.UnitSpec

class ActionExecutorSpec extends UnitSpec {
  "A NoOp ActionExecutor" should "provide no history" in {
    val executor = ActionExecutor.NoOp

    executor.history.await should be(ActionExecutor.History.empty)
  }

  "ActionExecutor History" should "support adding and pruning entries" in {
    val history = ActionExecutor.History.empty

    history.available should be(empty)
    history.discarded should be(0)

    val now = Instant.now()

    val entry = ActionExecutor.History.Entry(
      action = "test",
      trigger = "test",
      started = now,
      completed = now,
      successful = true
    )

    val updated = history
      .withEntry(entry)
      .withEntry(entry)
      .withEntry(entry)
      .withEntry(entry)
      .withEntry(entry)

    updated.available.length should be(5)
    updated.discarded should be(0)

    val unpruned = updated.prune(maxSize = 10)
    unpruned should be(updated)

    val pruned = updated.prune(maxSize = 3)

    pruned.available.length should be(3)
    pruned.discarded should be(2)
  }
}
