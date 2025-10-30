package io.github.sndnv.layers.events.exceptions

final case class EventCollectionFailure(message: String) extends Exception(message)
