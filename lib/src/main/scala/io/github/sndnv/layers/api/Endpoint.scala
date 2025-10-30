package io.github.sndnv.layers.api

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

import io.github.sndnv.layers.security.tls.EndpointContext
import org.apache.pekko.http.scaladsl.Http
import org.slf4j.Logger

/**
  * Generic API endpoint interface.
  */
trait Endpoint {

  /**
    * Endpoint name, used for logging.
    */
  def name: String

  protected def log: Logger

  /**
    * Starts the endpoint on the provided interface/port, with the specified context.
    *
    * @param interface interface to bind to
    * @param port port to bind to
    * @param context TLS context, if any
    */
  def start(interface: String, port: Int, context: Option[EndpointContext]): Unit =
    bind(interface = interface, port = port, context = context)
      .onComplete {
        case Success(_) =>
          log.info(
            "Endpoint [{}] successfully started on [{}:{}]",
            name,
            interface,
            port
          )

        case Failure(e) =>
          val cause = Option(e.getCause).getOrElse(e)
          log.error(
            "Failed to start [{}] endpoint on [{}:{}]: [{} - {}]",
            name,
            interface,
            port,
            cause.getClass.getSimpleName,
            cause.getMessage
          )
      }(ExecutionContext.parasitic)

  /**
    * Creates an endpoint binding on the provided interface/port, with the specified context.
    *
    * @param interface interface to bind to
    * @param port port to bind to
    * @param context TLS context, if any
    * @return the endpoint binding
    */
  def bind(interface: String, port: Int, context: Option[EndpointContext]): Future[Http.ServerBinding]
}
