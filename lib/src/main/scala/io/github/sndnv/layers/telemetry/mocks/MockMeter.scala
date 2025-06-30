package io.github.sndnv.layers.telemetry.mocks

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

import scala.jdk.CollectionConverters._

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics._
import io.opentelemetry.context.Context

@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
class MockMeter extends Meter with MockMeter.MeterContext { ctx: MockMeter.MeterContext =>
  import MockMeter._

  private[mocks] override val metrics: ConcurrentHashMap[String, AtomicLong] =
    new ConcurrentHashMap()

  private[mocks] override val callbacks: ConcurrentHashMap[
    String,
    Either[Consumer[ObservableLongMeasurement], Consumer[ObservableDoubleMeasurement]]
  ] = new ConcurrentHashMap()

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def metric(name: String): Long = Option(metrics.get(name)) match {
    case Some(value) => value.get()
    case None        => throw new IllegalArgumentException(s"Metric [$name] not found")
  }

  def collect(): Unit =
    callbacks.asScala.foreach {
      case (metric, Left(callback)) =>
        callback.accept(
          new ObservableLongMeasurement {
            override def record(value: Long): Unit =
              record(value, Attributes.empty())

            override def record(value: Long, attributes: Attributes): Unit = {
              val _ = metrics.get(metric).incrementAndGet()
            }
          }
        )

      case (metric, Right(callback)) =>
        callback.accept(
          new ObservableDoubleMeasurement {
            override def record(value: Double): Unit =
              record(value, Attributes.empty())

            override def record(value: Double, attributes: Attributes): Unit = {
              val _ = metrics.get(metric).incrementAndGet()
            }
          }
        )
    }

  override def counterBuilder(name: String): LongCounterBuilder = new LongCounterBuilder {
    override def setDescription(description: String): LongCounterBuilder =
      this

    override def setUnit(unit: String): LongCounterBuilder =
      this

    override def ofDoubles(): DoubleCounterBuilder = new DoubleCounterBuilder {
      override def setDescription(description: String): DoubleCounterBuilder = this

      override def setUnit(unit: String): DoubleCounterBuilder = this

      override def build(): DoubleCounter = new MockDoubleCounter(name, ctx)

      override def buildWithCallback(callback: Consumer[ObservableDoubleMeasurement]): ObservableDoubleCounter = {
        metrics.put(name, new AtomicLong(0))
        callbacks.put(name, Right(callback))
        new ObservableDoubleCounter {}
      }
    }

    override def build(): LongCounter = new MockLongCounter(name, ctx)

    override def buildWithCallback(callback: Consumer[ObservableLongMeasurement]): ObservableLongCounter = {
      metrics.put(name, new AtomicLong(0))
      callbacks.put(name, Left(callback))
      new ObservableLongCounter {}
    }
  }

  override def upDownCounterBuilder(name: String): LongUpDownCounterBuilder = new LongUpDownCounterBuilder {
    override def setDescription(description: String): LongUpDownCounterBuilder =
      this

    override def setUnit(unit: String): LongUpDownCounterBuilder =
      this

    override def ofDoubles(): DoubleUpDownCounterBuilder = new DoubleUpDownCounterBuilder {
      override def setDescription(description: String): DoubleUpDownCounterBuilder = this

      override def setUnit(unit: String): DoubleUpDownCounterBuilder = this

      override def build(): DoubleUpDownCounter = new MockDoubleUpDownCounter(name, ctx)

      override def buildWithCallback(callback: Consumer[ObservableDoubleMeasurement]): ObservableDoubleUpDownCounter = {
        metrics.put(name, new AtomicLong(0))
        callbacks.put(name, Right(callback))
        new ObservableDoubleUpDownCounter {}
      }
    }

    override def build(): LongUpDownCounter = new MockLongUpDownCounter(name, ctx)

    override def buildWithCallback(callback: Consumer[ObservableLongMeasurement]): ObservableLongUpDownCounter = {
      metrics.put(name, new AtomicLong(0))
      callbacks.put(name, Left(callback))
      new ObservableLongUpDownCounter {}
    }
  }

  override def histogramBuilder(name: String): DoubleHistogramBuilder = new DoubleHistogramBuilder {
    override def setDescription(description: String): DoubleHistogramBuilder =
      this

    override def setUnit(unit: String): DoubleHistogramBuilder =
      this

    override def ofLongs(): LongHistogramBuilder = new LongHistogramBuilder {
      override def setDescription(description: String): LongHistogramBuilder =
        this

      override def setUnit(unit: String): LongHistogramBuilder =
        this

      override def build(): LongHistogram = new MockLongHistogram(name, ctx)
    }

    override def build(): DoubleHistogram = new MockDoubleHistogram(name, ctx)
  }

  override def gaugeBuilder(name: String): DoubleGaugeBuilder = new DoubleGaugeBuilder {
    override def setDescription(description: String): DoubleGaugeBuilder =
      this

    override def setUnit(unit: String): DoubleGaugeBuilder =
      this

    override def ofLongs(): LongGaugeBuilder = new LongGaugeBuilder {
      override def setDescription(description: String): LongGaugeBuilder =
        this

      override def setUnit(unit: String): LongGaugeBuilder =
        this

      override def buildWithCallback(callback: Consumer[ObservableLongMeasurement]): ObservableLongGauge = {
        metrics.put(name, new AtomicLong(0))
        callbacks.put(name, Left(callback))
        new ObservableLongGauge {}
      }

      override def build(): LongGauge = new MockLongGauge(name, ctx)
    }

    override def buildWithCallback(callback: Consumer[ObservableDoubleMeasurement]): ObservableDoubleGauge = {
      metrics.put(name, new AtomicLong(0))
      callbacks.put(name, Right(callback))
      new ObservableDoubleGauge {}
    }

    override def build(): DoubleGauge = new MockDoubleGauge(name, ctx)
  }
}

@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
object MockMeter {
  def apply(): MockMeter = new MockMeter()

  trait MeterContext {
    private[mocks] def metrics: ConcurrentHashMap[String, AtomicLong]

    private[mocks] def callbacks
      : ConcurrentHashMap[String, Either[Consumer[ObservableLongMeasurement], Consumer[ObservableDoubleMeasurement]]]
  }

  class MockDoubleCounter(name: String, ctx: MeterContext) extends DoubleCounter {
    ctx.metrics.put(name, new AtomicLong(0))

    override def add(value: Double): Unit =
      add(value, Attributes.empty(), Context.current())

    override def add(value: Double, attributes: Attributes): Unit =
      add(value, attributes, Context.current())

    override def add(value: Double, attributes: Attributes, context: Context): Unit = {
      val _ = ctx.metrics.get(name).incrementAndGet()
    }
  }

  class MockLongCounter(name: String, ctx: MeterContext) extends LongCounter {
    ctx.metrics.put(name, new AtomicLong(0))

    override def add(value: Long): Unit =
      add(value, Attributes.empty(), Context.current())

    override def add(value: Long, attributes: Attributes): Unit =
      add(value, attributes, Context.current())

    override def add(value: Long, attributes: Attributes, context: Context): Unit = {
      val _ = ctx.metrics.get(name).incrementAndGet()
    }
  }

  class MockDoubleUpDownCounter(name: String, ctx: MeterContext) extends DoubleUpDownCounter {
    ctx.metrics.put(name, new AtomicLong(0))

    override def add(value: Double): Unit =
      add(value, Attributes.empty(), Context.current())

    override def add(value: Double, attributes: Attributes): Unit =
      add(value, attributes, Context.current())

    override def add(value: Double, attributes: Attributes, context: Context): Unit = {
      val _ = ctx.metrics.get(name).incrementAndGet()
    }
  }

  class MockLongUpDownCounter(name: String, ctx: MeterContext) extends LongUpDownCounter {
    ctx.metrics.put(name, new AtomicLong(0))

    override def add(value: Long): Unit =
      add(value, Attributes.empty(), Context.current())

    override def add(value: Long, attributes: Attributes): Unit =
      add(value, attributes, Context.current())

    override def add(value: Long, attributes: Attributes, context: Context): Unit = {
      val _ = ctx.metrics.get(name).incrementAndGet()
    }
  }

  class MockLongHistogram(name: String, ctx: MeterContext) extends LongHistogram {
    ctx.metrics.put(name, new AtomicLong(0))

    override def record(value: Long): Unit =
      record(value, Attributes.empty(), Context.current())

    override def record(value: Long, attributes: Attributes): Unit =
      record(value, attributes, Context.current())

    override def record(value: Long, attributes: Attributes, context: Context): Unit = {
      val _ = ctx.metrics.get(name).incrementAndGet()
    }
  }

  class MockDoubleHistogram(name: String, ctx: MeterContext) extends DoubleHistogram {
    ctx.metrics.put(name, new AtomicLong(0))

    override def record(value: Double): Unit =
      record(value, Attributes.empty(), Context.current())

    override def record(value: Double, attributes: Attributes): Unit =
      record(value, attributes, Context.current())

    override def record(value: Double, attributes: Attributes, context: Context): Unit = {
      val _ = ctx.metrics.get(name).incrementAndGet()
    }
  }

  class MockLongGauge(name: String, ctx: MeterContext) extends LongGauge {
    ctx.metrics.put(name, new AtomicLong(0))

    override def set(value: Long): Unit =
      set(value, Attributes.empty(), Context.current())

    override def set(value: Long, attributes: Attributes): Unit =
      set(value, attributes, Context.current())

    override def set(value: Long, attributes: Attributes, context: Context): Unit = {
      val _ = ctx.metrics.get(name).incrementAndGet()
    }
  }

  class MockDoubleGauge(name: String, ctx: MeterContext) extends DoubleGauge {
    ctx.metrics.put(name, new AtomicLong(0))

    override def set(value: Double): Unit =
      set(value, Attributes.empty(), Context.current())

    override def set(value: Double, attributes: Attributes): Unit =
      set(value, attributes, Context.current())

    override def set(value: Double, attributes: Attributes, context: Context): Unit = {
      val _ = ctx.metrics.get(name).incrementAndGet()
    }
  }
}
