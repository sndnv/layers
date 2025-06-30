package io.github.sndnv.layers.testing

import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.Future

class UnitSpecSpec extends UnitSpec {
  "A UnitSpec" should "support retrying tests (synchronous)" in {
    val total = new AtomicInteger(0)
    val successful = new AtomicInteger(0)

    withRetry(times = 1) {
      total.incrementAndGet()

      total.get() should be(2)

      successful.incrementAndGet()

      succeed
    }.await

    total.get() should be(2)
    successful.get() should be(1)
  }

  it should "support retrying tests (asynchronous)" in {
    val total = new AtomicInteger(0)
    val successful = new AtomicInteger(0)

    withRetry(times = 1) {
      Future {
        total.incrementAndGet()
        total.get() should be(2)
        successful.incrementAndGet()
        succeed
      }(scala.concurrent.ExecutionContext.global)
    }.await

    total.get() should be(2)
    successful.get() should be(1)
  }
}
