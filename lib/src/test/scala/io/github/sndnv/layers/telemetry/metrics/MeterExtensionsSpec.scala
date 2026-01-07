package io.github.sndnv.layers.telemetry.metrics

import io.github.sndnv.layers.testing.UnitSpec
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.LongHistogram
import io.opentelemetry.api.metrics.LongUpDownCounter
import io.opentelemetry.api.metrics.MeterProvider
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.*

class MeterExtensionsSpec extends UnitSpec {
  import io.github.sndnv.layers.telemetry.metrics.MeterExtensions.*

  "MeterExtensions" should "support creating counters" in {
    val meter = MeterProvider.noop().get("MeterExtensionsSpec")
    meter.counter(name = "test-counter-1") should be(a[Counter])
    meter.counter(name = "test-counter-2", description = "test-description") should be(a[Counter])

    val mockCounter = mock(classOf[LongCounter])

    val counter = Counter(underlying = mockCounter)
    counter.add(value = 2)
    counter.add(value = 3, attributes = attributeKey -> "test-value")
    counter.inc()
    counter.inc(attributes = attributeKey -> "test-value")

    verify(mockCounter, times(4)).add(ArgumentMatchers.anyLong, ArgumentMatchers.any[Attributes])

    succeed
  }

  they should "support creating histograms" in {

    val meter = MeterProvider.noop().get("MeterExtensionsSpec")
    meter.histogram(name = "test-histogram-1") should be(a[Histogram])
    meter.histogram(name = "test-histogram-2", description = "test-description") should be(a[Histogram])

    val mockHistogram = mock(classOf[LongHistogram])

    val histogram = Histogram(underlying = mockHistogram)
    histogram.record(value = 2)
    histogram.record(value = 3, attributes = attributeKey -> "test-value")

    verify(mockHistogram, times(2)).record(ArgumentMatchers.anyLong, ArgumentMatchers.any[Attributes])

    succeed
  }

  they should "support creating up-down counters" in {

    val meter = MeterProvider.noop().get("MeterExtensionsSpec")
    meter.upDownCounter(name = "test-counter-1") should be(a[UpDownCounter])
    meter.upDownCounter(name = "test-counter-2", description = "test-description") should be(a[UpDownCounter])

    val mockCounter = mock(classOf[LongUpDownCounter])

    val counter = UpDownCounter(underlying = mockCounter)
    counter.add(value = 2)
    counter.add(value = 3, attributes = attributeKey -> "test-value")
    counter.inc()
    counter.inc(attributes = attributeKey -> "test-value")
    counter.dec()

    verify(mockCounter, times(5)).add(ArgumentMatchers.anyLong, ArgumentMatchers.any[Attributes])

    succeed
  }

  private val attributeKey: AttributeKey[String] = AttributeKey.stringKey("test-key")
}
