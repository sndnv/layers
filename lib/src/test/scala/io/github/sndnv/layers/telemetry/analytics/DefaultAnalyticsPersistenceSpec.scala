package io.github.sndnv.layers.telemetry.analytics

import java.time.Instant

import scala.concurrent.duration.*

import io.github.sndnv.layers.api.Formats.*
import io.github.sndnv.layers.telemetry.ApplicationInformation
import io.github.sndnv.layers.telemetry.mocks.MockAnalyticsClient
import io.github.sndnv.layers.testing.FileSystemHelpers
import io.github.sndnv.layers.testing.UnitSpec
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import play.api.libs.json.Json

class DefaultAnalyticsPersistenceSpec extends UnitSpec with FileSystemHelpers {
  "DefaultAnalyticsPersistence" should "cache entries locally" in {
    val (fs, _) = createMockFileSystem(setup = FileSystemHelpers.FileSystemSetup.Unix)

    val config = DefaultAnalyticsPersistence.Config(
      localCache = fs.getPath("test-cache.json"),
      keepEvents = true,
      keepFailures = true
    )

    val persistence = DefaultAnalyticsPersistence(config = config)

    config.localCache.exists should be(false)
    persistence.lastCached should be(Instant.EPOCH)

    persistence.cache(entry)

    config.localCache.exists should be(true)
    Json.parse(config.localCache.content.await).as[AnalyticsEntry] should be(entry)
    persistence.lastCached should be > Instant.EPOCH
  }

  it should "update an existing local cache" in {
    val (fs, _) = createMockFileSystem(setup = FileSystemHelpers.FileSystemSetup.Unix)

    val config = DefaultAnalyticsPersistence.Config(
      localCache = fs.getPath("test-cache.json"),
      keepEvents = true,
      keepFailures = true
    )

    val persistence = DefaultAnalyticsPersistence(config = config)

    persistence.cache(entry)

    config.localCache.exists should be(true)
    Json.parse(config.localCache.content.await).as[AnalyticsEntry] should be(entry)

    val updated = AnalyticsEntry
      .collected(app = ApplicationInformation.none)
      .withEvent(name = "other-event", attributes = Map.empty)

    persistence.cache(updated)

    Json.parse(config.localCache.content.await).as[AnalyticsEntry] should be(updated)
  }

  it should "handle failures when caching entries locally" in {
    val (fs, _) = createMockFileSystem(setup = FileSystemHelpers.FileSystemSetup.Unix)

    val config = DefaultAnalyticsPersistence.Config(
      localCache = fs.getPath("/a/b/c/"), // invalid configuration
      keepEvents = true,
      keepFailures = true
    )

    val persistence = DefaultAnalyticsPersistence(config = config)

    config.localCache.exists should be(false)
    persistence.lastCached should be(Instant.EPOCH)

    persistence.cache(entry)

    config.localCache.exists should be(false)
    persistence.lastCached should be(Instant.EPOCH)
  }

  it should "transmit entries remotely (with events ands failures)" in {
    val (fs, _) = createMockFileSystem(setup = FileSystemHelpers.FileSystemSetup.Unix)

    val config = DefaultAnalyticsPersistence.Config(
      localCache = fs.getPath("test-cache.json"),
      keepEvents = true,
      keepFailures = true
    )

    val client = MockAnalyticsClient()

    val persistence = DefaultAnalyticsPersistence(config = config)
      .withClientProvider(provider = AnalyticsClient.Provider(() => client))

    client.sent should be(0)
    client.lastEntry should be(empty)
    persistence.lastTransmitted should be(Instant.EPOCH)

    val _ = persistence.transmit(entry).await

    client.sent should be(1)
    client.lastEntry should be(Some(entry))
    persistence.lastTransmitted should be > Instant.EPOCH
  }

  it should "transmit entries remotely (without events ands failures)" in {
    val (fs, _) = createMockFileSystem(setup = FileSystemHelpers.FileSystemSetup.Unix)

    val config = DefaultAnalyticsPersistence.Config(
      localCache = fs.getPath("test-cache.json"),
      keepEvents = false,
      keepFailures = false
    )

    val client = MockAnalyticsClient()

    val persistence = DefaultAnalyticsPersistence(config = config)
      .withClientProvider(provider = AnalyticsClient.Provider(() => client))

    client.sent should be(0)
    client.lastEntry should be(empty)
    persistence.lastTransmitted should be(Instant.EPOCH)

    val _ = persistence.transmit(entry).await

    client.sent should be(1)
    persistence.lastTransmitted should be > Instant.EPOCH

    client.lastEntry match {
      case Some(actualEntry) =>
        actualEntry.runtime should be(entry.runtime)
        actualEntry.events should be(Seq.empty)
        actualEntry.failures should be(Seq.empty)
        actualEntry.created should be(entry.created)
        actualEntry.updated should not be entry.updated

      case None =>
        fail("Expected a result but none was found")
    }
  }

  it should "delay remote transmission if a client provider is not available" in {
    val (fs, _) = createMockFileSystem(setup = FileSystemHelpers.FileSystemSetup.Unix)

    val config = DefaultAnalyticsPersistence.Config(
      localCache = fs.getPath("test-cache.json"),
      keepEvents = false,
      keepFailures = false,
      clientProviderRetryInterval = 100.millis,
      clientProviderMaxRetries = 10
    )

    val client = MockAnalyticsClient()

    val persistence = DefaultAnalyticsPersistence(config = config)

    client.sent should be(0)
    client.lastEntry should be(empty)
    persistence.lastTransmitted should be(Instant.EPOCH)

    val result = persistence.transmit(entry)

    await(delay = 300.millis)

    client.sent should be(0)
    client.lastEntry should be(empty)
    persistence.lastTransmitted should be(Instant.EPOCH)
    result.isCompleted should be(false)

    persistence.withClientProvider(provider = AnalyticsClient.Provider(() => client))

    await(delay = 200.millis)

    client.sent should be(1)
    persistence.lastTransmitted should be > Instant.EPOCH
    result.isCompleted should be(true)

    client.lastEntry match {
      case Some(actualEntry) =>
        actualEntry.runtime should be(entry.runtime)
        actualEntry.events should be(Seq.empty)
        actualEntry.failures should be(Seq.empty)
        actualEntry.created should be(entry.created)
        actualEntry.updated should not be entry.updated

      case None =>
        fail("Expected a result but none was found")
    }
  }

  it should "fail remote transmission if a client provider is never available" in {
    val (fs, _) = createMockFileSystem(setup = FileSystemHelpers.FileSystemSetup.Unix)

    val config = DefaultAnalyticsPersistence.Config(
      localCache = fs.getPath("test-cache.json"),
      keepEvents = false,
      keepFailures = false,
      clientProviderRetryInterval = 100.millis,
      clientProviderMaxRetries = 3
    )

    val client = MockAnalyticsClient()

    val persistence = DefaultAnalyticsPersistence(config = config)

    client.sent should be(0)
    client.lastEntry should be(empty)
    persistence.lastTransmitted should be(Instant.EPOCH)

    persistence.transmit(entry).failed.map { e =>
      client.sent should be(0)
      client.lastEntry should be(empty)
      persistence.lastTransmitted should be(Instant.EPOCH)

      e.getMessage should be("Expected a client provider but none was found after [3] retries")
    }
  }

  it should "restore entries from local cache, when available" in {
    val (fs, _) = createMockFileSystem(setup = FileSystemHelpers.FileSystemSetup.Unix)

    val config = DefaultAnalyticsPersistence.Config(
      localCache = fs.getPath("test-cache.json"),
      keepEvents = true,
      keepFailures = true
    )

    val persistence = DefaultAnalyticsPersistence(config = config)

    config.localCache.exists should be(false)

    val _ = config.localCache.write(content = Json.toJson(entry).toString()).await

    config.localCache.exists should be(true)

    persistence.restore().map { actualEntry =>
      actualEntry should be(Some(entry))
    }
  }

  it should "not restore entries from local cache, when not available" in {
    val (fs, _) = createMockFileSystem(setup = FileSystemHelpers.FileSystemSetup.Unix)

    val config = DefaultAnalyticsPersistence.Config(
      localCache = fs.getPath("test-cache.json"),
      keepEvents = true,
      keepFailures = true
    )

    val persistence = DefaultAnalyticsPersistence(config = config)

    config.localCache.exists should be(false)

    persistence.restore().map { actualEntry =>
      actualEntry should be(empty)
    }
  }

  it should "cache and restore pending entries" in {
    val (fs, _) = createMockFileSystem(setup = FileSystemHelpers.FileSystemSetup.Unix)

    val config = DefaultAnalyticsPersistence.Config(
      localCache = fs.getPath("test-cache.json"),
      keepEvents = true,
      keepFailures = true
    )

    val persistence = DefaultAnalyticsPersistence(config = config)

    val localCachePending = fs.getPath("test-cache_pending.json")

    localCachePending.exists should be(false)

    val first = AnalyticsEntry
      .collected(app = ApplicationInformation.none)
      .withEvent(name = "test", attributes = Map.empty)
    val second = AnalyticsEntry
      .collected(app = ApplicationInformation.none)
      .withEvent(name = "test-a", attributes = Map.empty)

    persistence.cachePending(entries = Seq(first, second))

    localCachePending.exists should be(true)

    persistence.restorePending().map { restored =>
      restored.map(_.events.map(_.event)) should be(Seq(Seq("test"), Seq("test-a")))
    }
  }

  it should "update an existing pending cache" in {
    val (fs, _) = createMockFileSystem(setup = FileSystemHelpers.FileSystemSetup.Unix)

    val config = DefaultAnalyticsPersistence.Config(
      localCache = fs.getPath("test-cache.json"),
      keepEvents = true,
      keepFailures = true
    )

    val persistence = DefaultAnalyticsPersistence(config = config)

    val localCachePending = fs.getPath("test-cache_pending.json")

    val first = AnalyticsEntry
      .collected(app = ApplicationInformation.none)
      .withEvent(name = "test", attributes = Map.empty)
    val second = AnalyticsEntry
      .collected(app = ApplicationInformation.none)
      .withEvent(name = "test-a", attributes = Map.empty)

    persistence.cachePending(entries = Seq(first))

    localCachePending.exists should be(true)
    Json.parse(localCachePending.content.await).as[Seq[AnalyticsEntry]] should be(Seq(first))

    persistence.cachePending(entries = Seq(first, second))

    Json.parse(localCachePending.content.await).as[Seq[AnalyticsEntry]] should be(Seq(first, second))
  }

  it should "not restore pending entries, when not available" in {
    val (fs, _) = createMockFileSystem(setup = FileSystemHelpers.FileSystemSetup.Unix)

    val config = DefaultAnalyticsPersistence.Config(
      localCache = fs.getPath("test-cache.json"),
      keepEvents = true,
      keepFailures = true
    )

    val persistence = DefaultAnalyticsPersistence(config = config)

    fs.getPath("test-cache_pending.json").exists should be(false)

    persistence.restorePending().map { restored =>
      restored should be(empty)
    }
  }

  it should "not restore pending entries, when the pending cache is corrupted" in {
    val (fs, _) = createMockFileSystem(setup = FileSystemHelpers.FileSystemSetup.Unix)

    val config = DefaultAnalyticsPersistence.Config(
      localCache = fs.getPath("test-cache.json"),
      keepEvents = true,
      keepFailures = true
    )

    val persistence = DefaultAnalyticsPersistence(config = config)

    val localCachePending = fs.getPath("test-cache_pending.json")

    val _ = localCachePending.write(content = "{invalid").await

    localCachePending.exists should be(true)

    persistence.restorePending().map { restored =>
      restored should be(empty)
    }
  }

  it should "handle failures when caching pending entries locally" in {
    val (fs, _) = createMockFileSystem(setup = FileSystemHelpers.FileSystemSetup.Unix)

    val config = DefaultAnalyticsPersistence.Config(
      localCache = fs.getPath("/a/b/c/"), // invalid configuration
      keepEvents = true,
      keepFailures = true
    )

    val persistence = DefaultAnalyticsPersistence(config = config)

    persistence.cachePending(entries = Seq(entry))

    fs.getPath("/a/b/c_pending").exists should be(false)
  }

  it should "derive the pending cache path from the local cache path" in {
    val (fs, _) = createMockFileSystem(setup = FileSystemHelpers.FileSystemSetup.Unix)

    val withExtension = DefaultAnalyticsPersistence(
      config = DefaultAnalyticsPersistence.Config(
        localCache = fs.getPath("/test-file.json"),
        keepEvents = true,
        keepFailures = true
      )
    )

    val withoutExtension = DefaultAnalyticsPersistence(
      config = DefaultAnalyticsPersistence.Config(
        localCache = fs.getPath("/test-file"),
        keepEvents = true,
        keepFailures = true
      )
    )

    withExtension.cachePending(entries = Seq.empty)
    withoutExtension.cachePending(entries = Seq.empty)

    fs.getPath("/test-file_pending.json").exists should be(true)
    fs.getPath("/test-file_pending").exists should be(true)
  }

  it should "support comparing Instants" in {
    import io.github.sndnv.layers.telemetry.analytics.DefaultAnalyticsPersistence.ExtendedInstant

    val now = Instant.now()
    val before = now.minusSeconds(1)
    val later = now.plusSeconds(1)

    now.min(before) should be(before)
    now.min(later) should be(now)
    now.min(now) should be(now)

    now.max(before) should be(now)
    now.max(later) should be(later)
    now.max(now) should be(now)
  }

  private val entry: AnalyticsEntry = AnalyticsEntry
    .collected(app = ApplicationInformation.none)
    .withEvent(name = "test-event", attributes = Map.empty)
    .withFailure(message = "Test failure")

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "DefaultAnalyticsPersistenceSpec"
  )
}
