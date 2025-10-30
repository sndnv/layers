package io.github.sndnv.layers.events

import java.time.Instant
import java.util.UUID

import scala.util.Failure
import scala.util.Success

import io.github.sndnv.layers.events.Event._
import io.github.sndnv.layers.events.EventSpec.OtherType
import io.github.sndnv.layers.events.mocks.MockEventCollector
import io.github.sndnv.layers.testing.UnitSpec
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

class EventSpec extends UnitSpec {
  "An Event Recorder" should "support creating events" in {
    val recorder = Event
      .recorder(withEventName = "test")
      .withAttribute("a", "b")
      .withAttributes("c" -> "d", "e" -> "f")

    val eventA = recorder.create()
    val eventB = recorder.withAttribute("g" -> "h").create()
    val eventC = recorder.withoutAttributes().create()
    val eventD = recorder.createWithAttributes(attributes = "i" -> "j")

    eventA.attributes should be(Map[String, StringAttributeValue]("a" -> "b", "c" -> "d", "e" -> "f"))

    eventB.name should be("test")
    eventB.attributes should be(Map[String, StringAttributeValue]("a" -> "b", "c" -> "d", "e" -> "f", "g" -> "h"))

    eventC.name should be("test")
    eventC.attributes should be(empty)

    eventD.name should be("test")
    eventD.attributes should be(Map[String, StringAttributeValue]("a" -> "b", "c" -> "d", "e" -> "f", "i" -> "j"))
  }

  it should "support recording events" in {
    val recorder = Event
      .recorder(withEventName = "test")
      .withAttribute("a", "b")
      .withAttributes("c" -> "d", "e" -> "f")

    implicit val collector: MockEventCollector = MockEventCollector()

    recorder.record()
    recorder.withAttribute("g" -> "h").record()
    recorder.withoutAttributes().record()
    recorder.recordWithAttributes(attributes = "i" -> "j")

    collector.events.toList match {
      case eventA :: eventB :: eventC :: eventD :: Nil =>
        eventA.name should be("test")
        eventA.attributes should be(Map[String, StringAttributeValue]("a" -> "b", "c" -> "d", "e" -> "f"))

        eventB.name should be("test")
        eventB.attributes should be(Map[String, StringAttributeValue]("a" -> "b", "c" -> "d", "e" -> "f", "g" -> "h"))

        eventC.name should be("test")
        eventC.attributes should be(empty)

        eventD.name should be("test")
        eventD.attributes should be(Map[String, StringAttributeValue]("a" -> "b", "c" -> "d", "e" -> "f", "i" -> "j"))

      case other =>
        fail(s"Unexpected result received: [$other]")
    }
  }

  "An Event AttributeKey" should "support creating typed keys" in {
    Event.AttributeKey.forString(key = "test-key").withValue(value = "test-value")._2.value should be(a[String])
    Event.AttributeKey.forUuid(key = "test-key").withValue(value = UUID.randomUUID())._2.value should be(a[UUID])
    Event.AttributeKey.forBoolean(key = "test-key").withValue(value = true)._2.value should be(a[Boolean])
    Event.AttributeKey.forShort(key = "test-key").withValue(value = 1.toShort)._2.value should be(a[Short])
    Event.AttributeKey.forInt(key = "test-key").withValue(value = 2)._2.value should be(an[Int])
    Event.AttributeKey.forLong(key = "test-key").withValue(value = 3L)._2.value should be(a[Long])
    Event.AttributeKey.forDouble(key = "test-key").withValue(value = 4.5)._2.value should be(a[Double])
    Event.AttributeKey.forFloat(key = "test-key").withValue(value = 6.7.toFloat)._2.value should be(a[Float])
    Event.AttributeKey.forAny(key = "test-key").withValue(value = OtherType(value = "test-value"))._2.value should be(
      an[OtherType]
    )

    Event.AttributeKey.forStringList(key = "test-key").withValue(value = Seq("test-value"))._2.value.head should be(an[String])
    Event.AttributeKey.forUuidList(key = "test-key").withValue(value = Seq(UUID.randomUUID()))._2.value.head should be(an[UUID])
    Event.AttributeKey.forBooleanList(key = "test-key").withValue(value = Seq(true))._2.value.head should be(an[Boolean])
    Event.AttributeKey.forShortList(key = "test-key").withValue(value = Seq(1.toShort))._2.value.head should be(an[Short])
    Event.AttributeKey.forIntList(key = "test-key").withValue(value = Seq(2))._2.value.head should be(an[Int])
    Event.AttributeKey.forLongList(key = "test-key").withValue(value = Seq(3L))._2.value.head should be(an[Long])
    Event.AttributeKey.forDoubleList(key = "test-key").withValue(value = Seq(4.5))._2.value.head should be(an[Double])
    Event.AttributeKey.forFloatList(key = "test-key").withValue(value = Seq(6.7.toFloat))._2.value.head should be(an[Float])

    Event.AttributeKey
      .forAnyList(key = "test-key")
      .withValue(value = Seq(OtherType(value = "test-value")))
      ._2
      .value
      .head should be(an[OtherType])
  }

  it should "support creating attributes" in {
    val key = Event.AttributeKey[StringAttributeValue](key = "test-key")

    key.withValue(value = "test-value") should be("test-key" -> StringAttributeValue(value = "test-value"))
  }

  "Events" should "support individual attributes" in {
    ("a": StringAttributeValue).asString should be("a")

    (UUID.fromString("df3ed975-62aa-490e-a076-195c34c187de"): UuidAttributeValue).asString should be(
      "df3ed975-62aa-490e-a076-195c34c187de"
    )

    (true: BooleanAttributeValue).asString should be("true")
    (false: BooleanAttributeValue).asString should be("false")

    (1.toShort: ShortAttributeValue).asString should be("1")
    (2: IntAttributeValue).asString should be("2")
    (3.toLong: LongAttributeValue).asString should be("3")

    (4.toDouble: DoubleAttributeValue).asString should be("4.0")
    (5.toFloat: FloatAttributeValue).asString should be("5.0")

    (OtherType(value = "6"): AnyAttributeValue[OtherType]).asString should be("OtherType(6)")
  }

  they should "support list attributes" in {
    (Seq("a", "b"): StringListAttributeValue).asString should be("a,b")

    (Seq("e2a85de2-f5ad-44a4-b899-8bb1bb9bda4b", "df3ed975-62aa-490e-a076-195c34c187de").map(
      UUID.fromString
    ): UuidListAttributeValue).asString should be(
      "e2a85de2-f5ad-44a4-b899-8bb1bb9bda4b,df3ed975-62aa-490e-a076-195c34c187de"
    )

    (Seq(true, false): BooleanListAttributeValue).asString should be("true,false")

    (Seq(1.toShort, 2.toShort): ShortListAttributeValue).asString should be("1,2")
    (Seq(3, 4): IntListAttributeValue).asString should be("3,4")
    (Seq(5.toLong, 6.toLong): LongListAttributeValue).asString should be("5,6")

    (Seq(7.toDouble, 8.toDouble): DoubleListAttributeValue).asString should be("7.0,8.0")
    (Seq(9.toFloat, 10.toFloat): FloatListAttributeValue).asString should be("9.0,10.0")

    (Seq(OtherType(value = "11"), OtherType(value = "12")): AnyListAttributeValue[OtherType]).asString should be(
      "OtherType(11),OtherType(12)"
    )
  }

  they should "support providing a string description" in {
    val event = Event(
      name = "test",
      attributes = Map(
        "a" -> "b",
        "c" -> 2L,
        "d" -> 3.2d,
        "e" -> UUID.fromString("df3ed975-62aa-490e-a076-195c34c187de")
      ),
      timestamp = Instant.now()
    )

    event.description should be(
      s"name=test," +
        s"timestamp=${event.timestamp}," +
        s"attributes={a='b',c='2',d='3.2',e='df3ed975-62aa-490e-a076-195c34c187de'}"
    )
  }

  they should "support providing optional attributes" in {
    val event = Event(
      name = "test",
      attributes = Map(
        "a" -> "b",
        "c" -> 2L,
        "d" -> 3.2d,
        "e" -> UUID.fromString("df3ed975-62aa-490e-a076-195c34c187de"),
        "f" -> OtherType(value = "g")
      ),
      timestamp = Instant.now()
    )

    withClue("with attribute key") {
      event.attribute[StringAttributeValue](attributeKey = Event.AttributeKey(key = "a")) should be(
        Success(Some("b": StringAttributeValue))
      )

      event.attribute[LongAttributeValue](attributeKey = Event.AttributeKey(key = "c")) should be(
        Success(Some(2: LongAttributeValue))
      )

      event.attribute[DoubleAttributeValue](attributeKey = Event.AttributeKey(key = "d")) should be(
        Success(Some(3.2: DoubleAttributeValue))
      )

      event.attribute[UuidAttributeValue](attributeKey = Event.AttributeKey(key = "e")) should be(
        Success(Some(UUID.fromString("df3ed975-62aa-490e-a076-195c34c187de"): UuidAttributeValue))
      )

      event.attribute[AnyAttributeValue[OtherType]](attributeKey = Event.AttributeKey(key = "f")) should be(
        Success(Some(OtherType(value = "g"): AnyAttributeValue[OtherType]))
      )

      event.attribute[LongAttributeValue](attributeKey = Event.AttributeKey(key = "a")) match {
        case Failure(e: IllegalArgumentException) =>
          e.getMessage should be("Expected attribute [a] with type [LongAttributeValue] but [StringAttributeValue] found")

        case other =>
          fail(s"Unexpected result received: [$other]")
      }

      event.attribute[LongAttributeValue](attributeKey = Event.AttributeKey(key = "h")) should be(Success(None))
    }

    withClue("with attribute name") {
      event.attributeWithName[StringAttributeValue](name = "a") should be(Success(Some("b": StringAttributeValue)))
      event.attributeWithName[LongAttributeValue](name = "c") should be(Success(Some(2: LongAttributeValue)))
      event.attributeWithName[DoubleAttributeValue](name = "d") should be(Success(Some(3.2: DoubleAttributeValue)))
      event.attributeWithName[UuidAttributeValue](name = "e") should be(
        Success(Some(UUID.fromString("df3ed975-62aa-490e-a076-195c34c187de"): UuidAttributeValue))
      )
      event.attributeWithName[AnyAttributeValue[OtherType]](name = "f") should be(
        Success(Some(OtherType(value = "g"): AnyAttributeValue[OtherType]))
      )

      event.attributeWithName[LongAttributeValue](name = "a") match {
        case Failure(e: IllegalArgumentException) =>
          e.getMessage should be("Expected attribute [a] with type [LongAttributeValue] but [StringAttributeValue] found")

        case other =>
          fail(s"Unexpected result received: [$other]")
      }

      event.attributeWithName[LongAttributeValue](name = "h") should be(Success(None))
    }
  }

  they should "support providing required attributes" in {
    val event = Event(
      name = "test",
      attributes = Map(
        "a" -> "b",
        "c" -> 2L,
        "d" -> 3.2d,
        "e" -> UUID.fromString("df3ed975-62aa-490e-a076-195c34c187de"),
        "f" -> OtherType(value = "g")
      ),
      timestamp = Instant.now()
    )

    withClue("with attribute key") {
      event.requireAttribute[StringAttributeValue](attributeKey = Event.AttributeKey(key = "a")) should be(
        Success("b": StringAttributeValue)
      )

      event.requireAttribute[LongAttributeValue](attributeKey = Event.AttributeKey(key = "c")) should be(
        Success(2: LongAttributeValue)
      )

      event.requireAttribute[DoubleAttributeValue](attributeKey = Event.AttributeKey(key = "d")) should be(
        Success(3.2: DoubleAttributeValue)
      )

      event.requireAttribute[UuidAttributeValue](attributeKey = Event.AttributeKey(key = "e")) should be(
        Success(UUID.fromString("df3ed975-62aa-490e-a076-195c34c187de"): UuidAttributeValue)
      )

      event.requireAttribute[AnyAttributeValue[OtherType]](attributeKey = Event.AttributeKey(key = "f")) should be(
        Success(OtherType(value = "g"): AnyAttributeValue[OtherType])
      )

      event.requireAttribute[LongAttributeValue](attributeKey = Event.AttributeKey(key = "a")) match {
        case Failure(e: IllegalArgumentException) =>
          e.getMessage should be("Expected attribute [a] with type [LongAttributeValue] but [StringAttributeValue] found")

        case other =>
          fail(s"Unexpected result received: [$other]")
      }

      event.requireAttribute[LongAttributeValue](attributeKey = Event.AttributeKey(key = "h")) match {
        case Failure(e: IllegalArgumentException) =>
          e.getMessage should be("Missing required attribute [h]")

        case other =>
          fail(s"Unexpected result received: [$other]")
      }
    }

    withClue("with attribute name") {
      event.requireAttributeWithName[StringAttributeValue](name = "a") should be(Success("b": StringAttributeValue))
      event.requireAttributeWithName[LongAttributeValue](name = "c") should be(Success(2: LongAttributeValue))
      event.requireAttributeWithName[DoubleAttributeValue](name = "d") should be(Success(3.2: DoubleAttributeValue))
      event.requireAttributeWithName[UuidAttributeValue](name = "e") should be(
        Success(UUID.fromString("df3ed975-62aa-490e-a076-195c34c187de"): UuidAttributeValue)
      )
      event.requireAttributeWithName[AnyAttributeValue[OtherType]](name = "f") should be(
        Success(OtherType(value = "g"): AnyAttributeValue[OtherType])
      )

      event.requireAttributeWithName[LongAttributeValue](name = "a") match {
        case Failure(e: IllegalArgumentException) =>
          e.getMessage should be("Expected attribute [a] with type [LongAttributeValue] but [StringAttributeValue] found")

        case other =>
          fail(s"Unexpected result received: [$other]")
      }

      event.requireAttributeWithName[LongAttributeValue](name = "h") match {
        case Failure(e: IllegalArgumentException) =>
          e.getMessage should be("Missing required attribute [h]")

        case other =>
          fail(s"Unexpected result received: [$other]")
      }
    }
  }

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "EventSpec"
  )
}

object EventSpec {
  final case class OtherType(value: String)
}
