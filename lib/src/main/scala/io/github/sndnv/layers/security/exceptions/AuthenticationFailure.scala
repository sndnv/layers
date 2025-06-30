package io.github.sndnv.layers.security.exceptions

final case class AuthenticationFailure(override val message: String) extends SecurityFailure(message)
