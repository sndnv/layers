package io.github.sndnv.layers.api

import java.time.Instant
import java.time.format.DateTimeParseException

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration

import org.apache.pekko.http.scaladsl.server.PathMatcher
import org.apache.pekko.http.scaladsl.server.PathMatcher1
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshaller

object Matchers {
  val IsoInstant: PathMatcher1[Instant] =
    PathMatcher("""\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d+)?Z""".r)
      .flatMap { string =>
        try Some(Instant.parse(string))
        catch { case _: DateTimeParseException => None }
      }

  implicit val stringToInstant: Unmarshaller[String, Instant] =
    Unmarshaller.strict(Instant.parse)

  implicit val stringToFiniteDuration: Unmarshaller[String, FiniteDuration] =
    Unmarshaller { _ => string =>
      Duration(string) match {
        case duration: FiniteDuration =>
          Future.successful(duration)

        case other =>
          Future.failed(
            new IllegalArgumentException(s"Expected FiniteDuration but [${other.toString}] provided")
          )
      }
    }
}
