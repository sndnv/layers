package io.github.sndnv.layers.telemetry.analytics

import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant

import scala.util.Using

import io.github.sndnv.layers.telemetry.ApplicationInformation

trait AnalyticsEntry {
  def runtime: AnalyticsEntry.RuntimeInformation
  def events: Seq[AnalyticsEntry.Event]
  def failures: Seq[AnalyticsEntry.Failure]
  def created: Instant
  def updated: Instant

  def asCollected(): AnalyticsEntry.Collected =
    AnalyticsEntry.Collected(
      runtime = runtime,
      events = events,
      failures = failures,
      created = created,
      updated = updated
    )
}

object AnalyticsEntry {
  def collected(app: ApplicationInformation): Collected = {
    val now = Instant.now()
    Collected(
      runtime = RuntimeInformation(app = app),
      events = Seq.empty,
      failures = Seq.empty,
      created = now,
      updated = now
    )
  }

  final case class Collected(
    override val runtime: AnalyticsEntry.RuntimeInformation,
    override val events: Seq[Event],
    override val failures: Seq[Failure],
    override val created: Instant,
    override val updated: Instant
  ) extends AnalyticsEntry {
    override def asCollected(): Collected = this

    def withEvent(name: String, attributes: Map[String, String]): Collected = {
      val event = AnalyticsEntry.uniqueEventFrom(name, attributes)
      copy(events = events :+ AnalyticsEntry.Event(id = events.length, event = event), updated = Instant.now())
    }

    def withFailure(message: String): Collected =
      withFailure(message = message, stackTrace = None)

    def withFailure(message: String, stackTrace: Option[String]): Collected =
      copy(
        failures = failures :+ AnalyticsEntry.Failure(
          message = AnalyticsEntry.Failure.anonymize(message),
          timestamp = Instant.now(),
          stackTrace = stackTrace.map(AnalyticsEntry.Failure.anonymize)
        ),
        updated = Instant.now()
      )

    def discardEvents(): Collected =
      copy(events = Seq.empty, updated = Instant.now())

    def discardFailures(): Collected =
      copy(failures = Seq.empty, updated = Instant.now())
  }

  final case class Event(
    id: Int,
    event: String
  )

  final case class Failure(
    message: String,
    timestamp: Instant,
    stackTrace: Option[String]
  )

  object Failure {
    def apply(message: String, timestamp: Instant): Failure =
      Failure(
        message = message,
        timestamp = timestamp,
        stackTrace = None
      )

    def anonymize(content: String): String =
      messageContainsPath
        .replaceAllIn(content, " *CONTENT_REMOVED* ")
        .replaceAll(repeatingWhitespace, " ")
        .trim

    def extractStackTrace(e: Throwable): Option[String] =
      Using(new StringWriter()) { writer =>
        Using.resource(new PrintWriter(writer))(e.printStackTrace)
        writer.toString.trim
      }.toOption.filterNot(_.isBlank)

    private val messageContainsPath = """(?:[a-zA-Z]:\\|[\\/])(?:[\w\-. ]+[\\/])*[\w\-. ]+""".r
    private val repeatingWhitespace = """\s\s+"""
  }

  final case class RuntimeInformation(
    id: String,
    app: String,
    jre: String,
    os: String
  )

  object RuntimeInformation {
    def apply(app: ApplicationInformation): RuntimeInformation = RuntimeInformation(
      id = RuntimeId,
      app = app.asString(),
      jre = JRE.asString(),
      os = OS.asString()
    )

    val RuntimeId: String = java.util.UUID.randomUUID().toString

    object JRE {
      val version: String = System.getProperty("java.vm.version", "unknown")
      val vendor: String = System.getProperty("java.vm.vendor", "unknown")

      def asString(): String = s"$version;$vendor"
    }

    object OS {
      val arch: String = System.getProperty("os.arch", "unknown")
      val name: String = System.getProperty("os.name", "unknown")
      val version: String = System.getProperty("os.version", "unknown")

      def asString(): String = s"$name;$version;$arch"
    }
  }

  private def uniqueEventFrom(name: String, attributes: Map[String, String]): String =
    if (attributes.nonEmpty) {
      val flattened = attributes.toSeq.sortBy(_._1).map { case (k, v) => s"$k='$v'" }.mkString(",")
      s"$name{$flattened}"
    } else {
      name
    }
}
