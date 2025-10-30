package io.github.sndnv.layers.events

import java.time.Instant
import java.util.UUID

import scala.reflect.ClassTag
import scala.util.Failure
import scala.util.Success
import scala.util.Try

/**
  * Class representing events published to an [[EventCollector]].
  * <br/><br/>
  * Publishing an event can be done either manually/directly (1) or by using an [[Event.Recorder]] (2):
  * {{{
  * implicit val collector: EventCollector = ???
  *
  * // 1. manually creating and publishing the event
  * val event = Event(name = "test-event").copy(attributes = Map(...))
  * collector.publish(event)
  *
  * // 2. using a EventCollector.Recorder
  * val recorder = Event.recorder(withEventName = "test-event")
  * recorder.withAttributes(...).record()
  * // or
  * recorder.recordWithAttributes(...)
  * }}}
  *
  * It is best to use the [[Event.Recorder]] (2) as it will avoid creating events if
  * collection is disabled.
  *
  * @param name event name
  * @param attributes event attributes
  * @param timestamp time of occurrence of the event
  *
  * @see [[EventCollector]] for collector documentation
  * @see [[Event.Recorder]] for recording events
  * @see [[Event.AttributeKey]] for defining and using attribute keys
  */
final case class Event(
  name: String,
  attributes: Map[String, Event.AttributeValue],
  timestamp: Instant
) {

  /**
    * Structured event description.
    * <br/><br/>
    * The format of the description is:
    * {{{
    * "name=[event name],timestamp=[ISO-8601 timestamp],attributes=[comma-separated key-value pairs]"
    * }}}
    */
  lazy val description: String =
    s"name=$name," +
      s"timestamp=${timestamp.toString}," +
      s"attributes=${attributes.toSeq.sortBy(_._1).map(e => s"${e._1}='${e._2.asString}'").mkString("{", ",", "}")}"

  /**
    * Retrieves the attribute associated with the provided key or returns a failure if it is missing.
    *
    * @param attributeKey attribute key
    * @return successful result if the attribute is present or a failure if not
    */
  def requireAttribute[T <: Event.AttributeValue](attributeKey: Event.AttributeKey[T])(implicit tag: ClassTag[T]): Try[T] =
    requireAttributeWithName[T](name = attributeKey.key)(tag)

  /**
    * Retrieves the attribute associated with the provided name or returns a failure if it is missing.
    *
    * @param name attribute name
    * @return successful result if the attribute is present or a failure if not
    */
  def requireAttributeWithName[T <: Event.AttributeValue](name: String)(implicit tag: ClassTag[T]): Try[T] =
    attributeWithName[T](name = name)(tag).flatMap {
      case Some(value) => Success(value)
      case None        => Failure(new IllegalArgumentException(s"Missing required attribute [$name]"))
    }

  /**
    * Retrieves the attribute associated with the provided key, if any.
    *
    * @param attributeKey attribute key
    * @return successful result if the attribute is present or an empty result if not
    */
  def attribute[T <: Event.AttributeValue](attributeKey: Event.AttributeKey[T])(implicit tag: ClassTag[T]): Try[Option[T]] =
    attributeWithName[T](name = attributeKey.key)(tag)

  /**
    * Retrieves the attribute associated with the provided name, if any.
    *
    * @param name attribute name
    * @return successful result if the attribute is present or an empty result if not
    */
  def attributeWithName[T <: Event.AttributeValue](name: String)(implicit tag: ClassTag[T]): Try[Option[T]] =
    attributes.get(name) match {
      case Some(value: T) =>
        Success(Some(value))

      case Some(other) =>
        Failure(
          new IllegalArgumentException(
            s"Expected attribute [$name] with type [${tag.runtimeClass.getSimpleName}] " +
              s"but [${other.getClass.getSimpleName}] found"
          )
        )

      case None =>
        Success(None)
    }
}

/**
  * @see [[Event]]
  */
object Event {

  /**
    * Type alias for an event that will be lazily created, if needed.
    */
  type Lazy = () => Event

  /**
    * Class allowing the definition and recording of common events and their attributes.
    *
    * @param eventName name for all events created by this recorder
    * @param eventAttributes shared attributes (if any) for all events created by this recorder
    *
    * @see [[Event]] for more information on publishing events
    */
  final case class Recorder private (eventName: String, eventAttributes: Map[String, AttributeValue]) {

    /**
      * Records a new event that uses the already specified name and attributes.
      *
      * @see [[Recorder.recordWithAttributes]] recording with extra attribute
      */
    def record()(implicit collector: EventCollector): Unit =
      collector.publish(event = create())

    /**
      * Records a new event that uses the already specified name and attributes,
      * adding the extra attributes provided here.
      *
      * @param attributes extra attributes to add to the existing set (if any)
      *
      * @see [[Recorder.record]] recording without extra attribute
      */
    def recordWithAttributes(attributes: (String, AttributeValue)*)(implicit collector: EventCollector): Unit =
      collector.publish(createWithAttributes(attributes = attributes: _*))

    /**
      * Creates a new event that uses the already specified name and attributes.
      *
      * @return the new event
      */
    def create(): Event =
      Event(
        name = eventName,
        attributes = eventAttributes,
        timestamp = Instant.now()
      )

    /**
      * Creates a new event that uses the already specified name and attributes,
      * adding the extra attributes provided here.
      *
      * @param attributes extra attributes to add to the existing set (if any)
      * @return the new event
      */
    def createWithAttributes(attributes: (String, AttributeValue)*): Event =
      Event(
        name = eventName,
        attributes = eventAttributes ++ attributes,
        timestamp = Instant.now()
      )

    /**
      * Adds the specified attribute key-value pair to the existing recorder attributes.
      *
      * @param attributeKey attribute key
      * @param attributeValue attribute value
      * @return a new recorder with the extra attribute
      */
    def withAttribute(attributeKey: String, attributeValue: AttributeValue): Recorder =
      copy(eventAttributes = eventAttributes + (attributeKey -> attributeValue))

    /**
      * Adds the specified attribute key-value pair to the existing recorder attributes.
      *
      * @param attribute attribute key-value pair
      * @return a new recorder with the extra attribute
      */
    def withAttribute(attribute: (String, AttributeValue)): Recorder =
      copy(eventAttributes = eventAttributes + attribute)

    /**
      * Adds the specified attribute key-value pairs to the existing recorder attributes.
      *
      * @param attributes attribute key-value pairs
      * @return a new recorder with the extra attributes
      */
    def withAttributes(attributes: (String, AttributeValue)*): Recorder =
      copy(eventAttributes = this.eventAttributes ++ attributes)

    /**
      * Removes all attribute from this recorder.
      *
      * @return a new recorder without attributes
      */
    def withoutAttributes(): Recorder =
      copy(eventAttributes = Map.empty)
  }

  /**
    * Creates a new recorder with the specified event name and no attributes.
    *
    * @param withEventName the name to use for recorded events
    * @return the new recorder
    */
  def recorder(withEventName: String): Recorder =
    Recorder(eventName = withEventName, eventAttributes = Map.empty)

  /**
    * Creates a new event with the specified name and no attributes.
    *
    * @param name event name
    * @return the new event
    */
  def apply(name: String): Event =
    Event(
      name = name,
      attributes = Map.empty,
      timestamp = Instant.now
    )

  /**
    * Class representing attribute keys/names.
    * <br/><br/>
    * Allows for defining and reusing attribute keys:
    * {{{
    *   val key1 = AttributeKey.forString("key-1")
    *   val key2 = AttributeKey.forInt("key-2")
    *   val key3: AttributeKey[BooleanAttributeValue] = AttributeKey("key-3")
    *
    *   val recorder = Event.recorder("test-event")
    *
    *   recorder.recordWithAttributes(
    *     key1.withValue("value-1"),
    *     key2.withValue(2),
    *     key3.withValue(true),
    *     key1.withValue("value-4"),
    *     key1.withValue("value-5"),
    *   )
    *
    *   // ... will create an event with the following attributes:
    *   Map(
    *     "key-1" -> "value-1",
    *     "key-2" -> "2",
    *     "key-3" -> "true",
    *     "key-1" -> "value-4",
    *     "key-1" -> "value-5",
    *   )
    * }}}
    *
    * @param key attribute key/name
    *
    * @see [[Recorder]] for recording events
    */
  final case class AttributeKey[T <: Event.AttributeValue](key: String) extends AnyVal {

    /**
      * Creates a new attribute key-value pair with the current key and the provided value.
      *
      * @param value value to use for the pair
      * @return the created pair
      */
    def withValue(value: T): (String, T) =
      key -> value
  }

  /**
    * @see [[AttributeKey]]
    */
  object AttributeKey {

    /**
      * Creates a new attribute key with a [[java.lang.String]] value.
      *
      * @param key attribute key/name
      * @return the new key
      */
    def forString(key: String): AttributeKey[StringAttributeValue] = AttributeKey[StringAttributeValue](key = key)

    /**
      * Creates a new attribute key with a [[java.util.UUID]] value.
      *
      * @param key attribute key/name
      * @return the new key
      */
    def forUuid(key: String): AttributeKey[UuidAttributeValue] = AttributeKey[UuidAttributeValue](key = key)

    /**
      * Creates a new attribute key with a `Boolean` value.
      *
      * @param key attribute key/name
      * @return the new key
      */
    def forBoolean(key: String): AttributeKey[BooleanAttributeValue] = AttributeKey[BooleanAttributeValue](key = key)

    /**
      * Creates a new attribute key with a `Short` value.
      *
      * @param key attribute key/name
      * @return the new key
      */
    def forShort(key: String): AttributeKey[ShortAttributeValue] = AttributeKey[ShortAttributeValue](key = key)

    /**
      * Creates a new attribute key with an `Int` value.
      *
      * @param key attribute key/name
      * @return the new key
      */
    def forInt(key: String): AttributeKey[IntAttributeValue] = AttributeKey[IntAttributeValue](key = key)

    /**
      * Creates a new attribute key with a `Long` value.
      *
      * @param key attribute key/name
      * @return the new key
      */
    def forLong(key: String): AttributeKey[LongAttributeValue] = AttributeKey[LongAttributeValue](key = key)

    /**
      * Creates a new attribute key with a `Double` value.
      *
      * @param key attribute key/name
      * @return the new key
      */
    def forDouble(key: String): AttributeKey[DoubleAttributeValue] = AttributeKey[DoubleAttributeValue](key = key)

    /**
      * Creates a new attribute key with a `Float` value.
      *
      * @param key attribute key/name
      * @return the new key
      */
    def forFloat(key: String): AttributeKey[FloatAttributeValue] = AttributeKey[FloatAttributeValue](key = key)

    /**
      * Creates a new attribute key with a value of the provided generic type.
      *
      * @param key attribute key/name
      * @return the new key
      */
    def forAny[T](key: String): AttributeKey[AnyAttributeValue[T]] = AttributeKey[AnyAttributeValue[T]](key = key)

    /**
      * Creates a new attribute key with a [[java.lang.String]] list value.
      *
      * @param key attribute key/name
      * @return the new key
      */
    def forStringList(key: String): AttributeKey[StringListAttributeValue] = AttributeKey[StringListAttributeValue](key = key)

    /**
      * Creates a new attribute key with a [[java.util.UUID]] list value.
      *
      * @param key attribute key/name
      * @return the new key
      */
    def forUuidList(key: String): AttributeKey[UuidListAttributeValue] = AttributeKey[UuidListAttributeValue](key = key)

    /**
      * Creates a new attribute key with a `Boolean` list value.
      *
      * @param key attribute key/name
      * @return the new key
      */
    def forBooleanList(key: String): AttributeKey[BooleanListAttributeValue] = AttributeKey[BooleanListAttributeValue](key = key)

    /**
      * Creates a new attribute key with a `Short` list value.
      *
      * @param key attribute key/name
      * @return the new key
      */
    def forShortList(key: String): AttributeKey[ShortListAttributeValue] = AttributeKey[ShortListAttributeValue](key = key)

    /**
      * Creates a new attribute key with an `Int` list value.
      *
      * @param key attribute key/name
      * @return the new key
      */
    def forIntList(key: String): AttributeKey[IntListAttributeValue] = AttributeKey[IntListAttributeValue](key = key)

    /**
      * Creates a new attribute key with a `Long` list value.
      *
      * @param key attribute key/name
      * @return the new key
      */
    def forLongList(key: String): AttributeKey[LongListAttributeValue] = AttributeKey[LongListAttributeValue](key = key)

    /**
      * Creates a new attribute key with a `Double` list value.
      *
      * @param key attribute key/name
      * @return the new key
      */
    def forDoubleList(key: String): AttributeKey[DoubleListAttributeValue] = AttributeKey[DoubleListAttributeValue](key = key)

    /**
      * Creates a new attribute key with a `Float` list value.
      *
      * @param key attribute key/name
      * @return the new key
      */
    def forFloatList(key: String): AttributeKey[FloatListAttributeValue] = AttributeKey[FloatListAttributeValue](key = key)

    /**
      * Creates a new attribute key with a value of a list of the provided generic type.
      *
      * @param key attribute key/name
      * @return the new key
      */
    def forAnyList[T](key: String): AttributeKey[AnyListAttributeValue[T]] = AttributeKey[AnyListAttributeValue[T]](key = key)
  }

  /**
    * Generic interface for representing typed attribute values.
    * <br/><br/>
    * The available attributes value types are:
    * <ul>
    *   <li>[[StringAttributeValue]]</li>
    *   <li>[[UuidAttributeValue]]</li>
    *   <li>[[BooleanAttributeValue]]</li>
    *   <li>[[ShortAttributeValue]]</li>
    *   <li>[[IntAttributeValue]]</li>
    *   <li>[[LongAttributeValue]]</li>
    *   <li>[[DoubleAttributeValue]]</li>
    *   <li>[[FloatAttributeValue]]</li>
    *   <li>[[AnyAttributeValue]]</li>
    *   <li>[[StringListAttributeValue]]</li>
    *   <li>[[UuidListAttributeValue]]</li>
    *   <li>[[BooleanListAttributeValue]]</li>
    *   <li>[[ShortListAttributeValue]]</li>
    *   <li>[[IntListAttributeValue]]</li>
    *   <li>[[LongListAttributeValue]]</li>
    *   <li>[[DoubleListAttributeValue]]</li>
    *   <li>[[FloatListAttributeValue]]</li>
    *   <li>[[AnyListAttributeValue]]</li>
    * </ul>
    */
  sealed trait AttributeValue extends Any {

    /**
      *  Converts the attribute value to string.
      */
    def asString: String
  }

  final case class StringAttributeValue(value: String) extends AnyVal with AttributeValue {
    override def asString: String = value
  }

  final case class UuidAttributeValue(value: UUID) extends AnyVal with AttributeValue {
    override def asString: String = value.toString
  }

  final case class BooleanAttributeValue(value: Boolean) extends AnyVal with AttributeValue {
    override def asString: String = value.toString
  }

  final case class ShortAttributeValue(value: Short) extends AnyVal with AttributeValue {
    override def asString: String = value.toString
  }

  final case class IntAttributeValue(value: Int) extends AnyVal with AttributeValue {
    override def asString: String = value.toString
  }

  final case class LongAttributeValue(value: Long) extends AnyVal with AttributeValue {
    override def asString: String = value.toString
  }

  final case class DoubleAttributeValue(value: Double) extends AnyVal with AttributeValue {
    override def asString: String = value.toString
  }

  final case class FloatAttributeValue(value: Float) extends AnyVal with AttributeValue {
    override def asString: String = value.toString
  }

  final case class AnyAttributeValue[T](value: T) extends AnyVal with AttributeValue {
    override def asString: String = value.toString
  }

  final case class StringListAttributeValue(value: Seq[String]) extends AnyVal with AttributeValue {
    override def asString: String = value.mkString(",")
  }

  final case class UuidListAttributeValue(value: Seq[UUID]) extends AnyVal with AttributeValue {
    override def asString: String = value.mkString(",")
  }

  final case class BooleanListAttributeValue(value: Seq[Boolean]) extends AnyVal with AttributeValue {
    override def asString: String = value.mkString(",")
  }

  final case class ShortListAttributeValue(value: Seq[Short]) extends AnyVal with AttributeValue {
    override def asString: String = value.mkString(",")
  }

  final case class IntListAttributeValue(value: Seq[Int]) extends AnyVal with AttributeValue {
    override def asString: String = value.mkString(",")
  }

  final case class LongListAttributeValue(value: Seq[Long]) extends AnyVal with AttributeValue {
    override def asString: String = value.mkString(",")
  }

  final case class DoubleListAttributeValue(value: Seq[Double]) extends AnyVal with AttributeValue {
    override def asString: String = value.mkString(",")
  }

  final case class FloatListAttributeValue(value: Seq[Float]) extends AnyVal with AttributeValue {
    override def asString: String = value.mkString(",")
  }

  final case class AnyListAttributeValue[T](value: Seq[T]) extends AnyVal with AttributeValue {
    override def asString: String = value.mkString(",")
  }

  import scala.language.implicitConversions

  implicit def stringToAttribute(value: String): StringAttributeValue = StringAttributeValue(value = value)
  implicit def uuidToAttribute(value: UUID): UuidAttributeValue = UuidAttributeValue(value = value)
  implicit def booleanToAttribute(value: Boolean): BooleanAttributeValue = BooleanAttributeValue(value = value)
  implicit def shortToAttribute(value: Short): ShortAttributeValue = ShortAttributeValue(value = value)
  implicit def intToAttribute(value: Int): IntAttributeValue = IntAttributeValue(value = value)
  implicit def longToAttribute(value: Long): LongAttributeValue = LongAttributeValue(value = value)
  implicit def doubleToAttribute(value: Double): DoubleAttributeValue = DoubleAttributeValue(value = value)
  implicit def floatToAttribute(value: Float): FloatAttributeValue = FloatAttributeValue(value = value)
  implicit def anyToAttribute[T](value: T): AnyAttributeValue[T] = AnyAttributeValue(value = value)

  implicit def stringListToAttribute(value: Seq[String]): StringListAttributeValue = StringListAttributeValue(value = value)
  implicit def uuidListToAttribute(value: Seq[UUID]): UuidListAttributeValue = UuidListAttributeValue(value = value)
  implicit def booleanListToAttribute(value: Seq[Boolean]): BooleanListAttributeValue = BooleanListAttributeValue(value = value)
  implicit def shortListToAttribute(value: Seq[Short]): ShortListAttributeValue = ShortListAttributeValue(value = value)
  implicit def intListToAttribute(value: Seq[Int]): IntListAttributeValue = IntListAttributeValue(value = value)
  implicit def longListToAttribute(value: Seq[Long]): LongListAttributeValue = LongListAttributeValue(value = value)
  implicit def doubleListToAttribute(value: Seq[Double]): DoubleListAttributeValue = DoubleListAttributeValue(value = value)
  implicit def floatListToAttribute(value: Seq[Float]): FloatListAttributeValue = FloatListAttributeValue(value = value)
  implicit def anyListToAttribute[T](value: Seq[T]): AnyListAttributeValue[T] = AnyListAttributeValue(value = value)
}
