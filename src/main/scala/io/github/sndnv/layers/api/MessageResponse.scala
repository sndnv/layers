package io.github.sndnv.layers.api

import play.api.libs.json.{Format, Json}

final case class MessageResponse(message: String)

object MessageResponse {
  implicit val messageResponseFormat: Format[MessageResponse] = Json.format[MessageResponse]
}
