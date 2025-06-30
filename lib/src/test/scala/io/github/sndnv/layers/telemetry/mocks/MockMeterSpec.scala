package io.github.sndnv.layers.telemetry.mocks

import java.util.concurrent.atomic.AtomicBoolean

import io.github.sndnv.layers.testing.UnitSpec
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.context.Context

class MockMeterSpec extends UnitSpec {
  "A MockMeter" should "provide counters (long)" in {
    val meter = MockMeter()

    val longCounter = meter
      .counterBuilder(name = "test-long")
      .setDescription("test")
      .setUnit("test")
      .build()

    meter.metric("test-long") should be(0)

    longCounter.add(1)
    longCounter.add(1, Attributes.empty())
    longCounter.add(1, Attributes.empty(), Context.current())

    meter.metric("test-long") should be(3)
  }

  it should "provide counters (double)" in {
    val meter = MockMeter()

    val doubleCounter = meter
      .counterBuilder(name = "test-double")
      .ofDoubles()
      .setDescription("test")
      .setUnit("test")
      .build()

    meter.metric("test-double") should be(0)

    doubleCounter.add(1)
    doubleCounter.add(1, Attributes.empty())
    doubleCounter.add(1, Attributes.empty(), Context.current())

    meter.metric("test-double") should be(3)
  }

  it should "provide up-down counters (long)" in {
    val meter = MockMeter()

    val longCounter = meter
      .upDownCounterBuilder(name = "test-long")
      .setDescription("test")
      .setUnit("test")
      .build()

    meter.metric("test-long") should be(0)

    longCounter.add(1)
    longCounter.add(1, Attributes.empty())
    longCounter.add(1, Attributes.empty(), Context.current())

    meter.metric("test-long") should be(3)
  }

  it should "provide up-down counters (double)" in {
    val meter = MockMeter()

    val doubleCounter = meter
      .upDownCounterBuilder(name = "test-double")
      .ofDoubles()
      .setDescription("test")
      .setUnit("test")
      .build()

    meter.metric("test-double") should be(0)

    doubleCounter.add(1)
    doubleCounter.add(1, Attributes.empty())
    doubleCounter.add(1, Attributes.empty(), Context.current())

    meter.metric("test-double") should be(3)
  }

  it should "provide histograms (long)" in {
    val meter = MockMeter()

    val longHistogram = meter
      .histogramBuilder(name = "test-long")
      .ofLongs()
      .setDescription("test")
      .setUnit("test")
      .build()

    meter.metric("test-long") should be(0)

    longHistogram.record(1)
    longHistogram.record(1, Attributes.empty())
    longHistogram.record(1, Attributes.empty(), Context.current())

    meter.metric("test-long") should be(3)
  }

  it should "provide histograms (double)" in {
    val meter = MockMeter()

    val doubleHistogram = meter
      .histogramBuilder(name = "test-double")
      .setDescription("test")
      .setUnit("test")
      .build()

    meter.metric("test-double") should be(0)

    doubleHistogram.record(1)
    doubleHistogram.record(1, Attributes.empty())
    doubleHistogram.record(1, Attributes.empty(), Context.current())

    meter.metric("test-double") should be(3)
  }

  it should "provide gauges (long)" in {
    val meter = MockMeter()

    val longHistogram = meter
      .gaugeBuilder(name = "test-long")
      .ofLongs()
      .setDescription("test")
      .setUnit("test")
      .build()

    meter.metric("test-long") should be(0)

    longHistogram.set(1)
    longHistogram.set(1, Attributes.empty())
    longHistogram.set(1, Attributes.empty(), Context.current())

    meter.metric("test-long") should be(3)
  }

  it should "provide gauges (double)" in {
    val meter = MockMeter()

    val doubleHistogram = meter
      .gaugeBuilder(name = "test-double")
      .setDescription("test")
      .setUnit("test")
      .build()

    meter.metric("test-double") should be(0)

    doubleHistogram.set(1)
    doubleHistogram.set(1, Attributes.empty())
    doubleHistogram.set(1, Attributes.empty(), Context.current())

    meter.metric("test-double") should be(3)
  }

  it should "provide counters (long) with callbacks" in {
    val meter = MockMeter()

    val callbackCalled = new AtomicBoolean(false)

    val _ = meter
      .counterBuilder(name = "test-long")
      .setDescription("test")
      .setUnit("test")
      .buildWithCallback { metric =>
        metric.record(1)
        callbackCalled.set(true)
      }

    meter.metric("test-long") should be(0)
    callbackCalled.get() should be(false)

    meter.collect()

    meter.metric("test-long") should be(1)
    callbackCalled.get() should be(true)
  }

  it should "provide counters (double) with callbacks" in {
    val meter = MockMeter()

    val callbackCalled = new AtomicBoolean(false)

    val _ = meter
      .counterBuilder(name = "test-double")
      .ofDoubles()
      .setDescription("test")
      .setUnit("test")
      .buildWithCallback { metric =>
        metric.record(1)
        callbackCalled.set(true)
      }

    meter.metric("test-double") should be(0)
    callbackCalled.get() should be(false)

    meter.collect()

    meter.metric("test-double") should be(1)
    callbackCalled.get() should be(true)
  }

  it should "provide up-down counters (long) with callbacks" in {
    val meter = MockMeter()

    val callbackCalled = new AtomicBoolean(false)

    val _ = meter
      .upDownCounterBuilder(name = "test-long")
      .setDescription("test")
      .setUnit("test")
      .buildWithCallback { metric =>
        metric.record(1)
        callbackCalled.set(true)
      }

    meter.metric("test-long") should be(0)
    callbackCalled.get() should be(false)

    meter.collect()

    meter.metric("test-long") should be(1)
    callbackCalled.get() should be(true)
  }

  it should "provide up-down counters (double) with callbacks" in {
    val meter = MockMeter()

    val callbackCalled = new AtomicBoolean(false)

    val _ = meter
      .upDownCounterBuilder(name = "test-double")
      .ofDoubles()
      .setDescription("test")
      .setUnit("test")
      .buildWithCallback { metric =>
        metric.record(1)
        callbackCalled.set(true)
      }

    meter.metric("test-double") should be(0)
    callbackCalled.get() should be(false)

    meter.collect()

    meter.metric("test-double") should be(1)
    callbackCalled.get() should be(true)
  }

  it should "provide gauges (long) with callbacks" in {
    val meter = MockMeter()

    val callbackCalled = new AtomicBoolean(false)

    val _ = meter
      .gaugeBuilder(name = "test-long")
      .ofLongs()
      .setDescription("test")
      .setUnit("test")
      .buildWithCallback { metric =>
        metric.record(1)
        callbackCalled.set(true)
      }

    meter.metric("test-long") should be(0)
    callbackCalled.get() should be(false)

    meter.collect()

    meter.metric("test-long") should be(1)
    callbackCalled.get() should be(true)
  }

  it should "provide gauges (double) with callbacks" in {
    val meter = MockMeter()

    val callbackCalled = new AtomicBoolean(false)

    val _ = meter
      .gaugeBuilder(name = "test-double")
      .setDescription("test")
      .setUnit("test")
      .buildWithCallback { metric =>
        metric.record(1)
        callbackCalled.set(true)
      }

    meter.metric("test-double") should be(0)
    callbackCalled.get() should be(false)

    meter.collect()

    meter.metric("test-double") should be(1)
    callbackCalled.get() should be(true)
  }

  it should "fail to provide missing metrics" in {
    val meter = MockMeter()

    an[IllegalArgumentException] should be thrownBy meter.metric(name = "test")
  }
}
