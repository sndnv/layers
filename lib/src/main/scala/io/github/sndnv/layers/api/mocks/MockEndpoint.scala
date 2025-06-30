package io.github.sndnv.layers.api.mocks

import java.util.concurrent.atomic.AtomicReference

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success
import scala.util.control.NonFatal
import scala.util.matching.Regex

import io.github.sndnv.layers.security.tls.EndpointContext
import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.ContentTypes
import org.apache.pekko.http.scaladsl.model.HttpEntity
import org.apache.pekko.http.scaladsl.model.HttpMethod
import org.apache.pekko.http.scaladsl.model.StatusCode
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.ExceptionHandler
import org.apache.pekko.http.scaladsl.server.RejectionHandler
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.util.ByteString
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class MockEndpoint {
  def port: Int
  def context: Option[EndpointContext]

  implicit def system: ActorSystem[Nothing]

  def routes: Route

  protected val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  protected val bindingRef: AtomicReference[Option[Http.ServerBinding]] = new AtomicReference(None)

  private val requests: mutable.Queue[MockEndpoint.Request] = new mutable.Queue()

  def start(): Future[Done] = {
    val server = {
      val builder = Http().newServerAt(interface = "localhost", port = port)

      context match {
        case Some(httpsContext) => builder.enableHttps(httpsContext.connection)
        case None               => builder
      }
    }

    implicit val ec: ExecutionContext = system.executionContext

    implicit val exceptionHandler: ExceptionHandler = ExceptionHandler { case NonFatal(e) =>
      log.error("Unhandled exception encountered: [{} - {}]", e.getClass.getSimpleName, e.getMessage)
      complete(StatusCodes.InternalServerError)
    }

    implicit val rejectionHandler: RejectionHandler = RejectionHandler
      .newBuilder()
      .handle { rejection =>
        extractRequest { request =>
          log.warn("[{}] request for [{}] rejected: [{}]", request.method.value, request.uri.path.toString, rejection)
          complete(StatusCodes.MisdirectedRequest)
        }
      }
      .result()
      .seal

    val sealedRoutes: Route = Route.seal(
      extractRequest { request =>
        mapResponse { response =>
          val _ = requests.enqueue(
            MockEndpoint.Request(
              method = request.method,
              path = request.uri.path.toString(),
              response = response.status
            )
          )
          response
        }(routes)
      }
    )

    val binding = server.bindFlow(handlerFlow = sealedRoutes)

    binding.onComplete {
      case Success(value) =>
        bindingRef.set(Some(value))

      case Failure(e) =>
        log.error("Server binding on port [{}] failed with [{}]", port, e.getMessage)
    }

    binding.map(_ => Done)
  }

  def stop(): Unit = bindingRef.get().foreach(_.terminate(hardDeadline = 250.millis))

  private val scheme = context match {
    case Some(_) => "https"
    case None    => "http"
  }

  def url: String = s"$scheme://localhost:${port.toString}"

  def resolve(path: String): String =
    s"$url/${path.stripPrefix("/")}"

  def count(path: String): Int =
    count(f = request => request.path == path)

  def count(path: Regex): Int =
    count(f = request => path.matches(request.path))

  def count(f: MockEndpoint.Request => Boolean): Int =
    requests.count(f)

  def binaryEntity(content: ByteString): HttpEntity.Strict =
    HttpEntity.Strict(contentType = ContentTypes.`application/octet-stream`, data = content)

  def plainTextEntity(content: String): HttpEntity.Strict =
    HttpEntity.Strict(contentType = ContentTypes.`text/plain(UTF-8)`, data = ByteString(content))

  def jsonEntity(content: String): HttpEntity.Strict =
    HttpEntity.Strict(contentType = ContentTypes.`application/json`, data = ByteString(content))
}

object MockEndpoint {
  final case class Request(method: HttpMethod, path: String, response: StatusCode)
}
