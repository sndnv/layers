package io.github.sndnv.layers.persistence.memory

import io.github.sndnv.layers.persistence.KeyValueStoreBehaviour
import io.github.sndnv.layers.testing.UnitSpec
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

class MemoryStoreSpec extends UnitSpec with KeyValueStoreBehaviour {
  "A MemoryStore" should behave like keyValueStore[MemoryStore[String, Int]](
    createStore = telemetry =>
      MemoryStore(name = "map-store")(
        s = ActorSystem(guardianBehavior = Behaviors.ignore, name = "MemoryStoreSpec"),
        telemetry = telemetry,
        t = timeout
      )
  )
}
