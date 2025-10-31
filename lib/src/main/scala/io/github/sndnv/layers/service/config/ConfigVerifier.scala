package io.github.sndnv.layers.service.config

import java.io.File

import scala.io.Source
import scala.jdk.CollectionConverters._
import scala.util.Failure
import scala.util.Success
import scala.util.Using
import scala.util.matching.Regex

import org.slf4j.Logger

/**
  * Configuration file verification.
  * <br/><br/>
  * Example:
  * {{{
  *   ConfigVerifier.verify() // uses application.conf by default
  *
  *   ConfigVerifier.verify("some-other-file") // will try to find some-other-file.conf
  * }}}
  *
  * Checks for:
  * <ul>
  *   <li><strong>deprecated environment variables</strong> - a warning is emitted if a deprecated env var is provided/used</li>
  * </ul>
  * <hr/>
  * Specifying <strong>deprecated environment variables</strong> is done by adding a comment (starting with `#` or `//`)
  * above the deprecated env var with <sup>(1)</sup> or without <sup>(2)</sup> mentioning a replacement env var:
  * {{{
  *   a {
  *     b = "test"
  *     // example (1)
  *     // deprecated, use NEW_ENV_VAR_FOR_B
  *     b = \${?SOME_DEPRECATED_ENV_VAR}
  *     b = \${?NEW_ENV_VAR_FOR_B}
  *     // example (2)
  *     // deprecated
  *     c = \${?SOME_OTHER_DEPRECATED_ENV_VAR}
  *     c = \${?NEW_ENV_VAR_FOR_C}
  *   }
  * }}}
  * If the deprecated environment variables (`SOME_DEPRECATED_ENV_VAR` and `SOME_OTHER_DEPRECATED_ENV_VAR`) are available
  * when the application starts, the following warnings will be seen in the logs:
  * {{{
  *   2020-01-01 00:00:00 WARN Environment variable [SOME_DEPRECATED_ENV_VAR] for parameter [b] is deprecated; use [NEW_ENV_VAR_FOR_B] instead
  *   2020-01-01 00:00:00 WARN Environment variable [SOME_OTHER_DEPRECATED_ENV_VAR] for parameter [c] is deprecated
  * }}}
  */
object ConfigVerifier {

  /**
    * Verifies the configuration in the default config file (`application.conf`),
    * and logs warnings/errors if any issues are found.
    *
    * @see [[ConfigVerifier]] for more details
    */
  def verify()(implicit log: Logger): Unit =
    verify(configBasename = "application")

  /**
    * Verifies the configuration in the provided file or resource name,
    * and logs warnings/errors if any issues are found.
    *
    * @param configBasename configuration file basename or full name/path
    *                       (ex: `application`, `application.conf`, `/opt/app/application.conf`)
    *
    * @see [[ConfigVerifier]] for more details
    */
  def verify(configBasename: String)(implicit log: Logger): Unit =
    verify(configBasename = configBasename, envVars = System.getenv().keySet().asScala.toSet)

  private[config] def verify(configBasename: String, envVars: Set[String])(implicit log: Logger): Unit = {
    val configFileName = if (configBasename.endsWith(".conf")) {
      configBasename
    } else {
      s"$configBasename.conf"
    }

    Using(
      Option(getClass.getClassLoader.getResource(configFileName))
        .map(resource => Source.fromURL(resource))
        .getOrElse(Source.fromFile(new File(configFileName)))
    ) { source =>
      source
        .getLines()
        .toSeq
        .map(_.trim)
        .filter(_.nonEmpty)
    }.map(parseDeprecations) match {
      case Success(deprecations) =>
        processDeprecations(
          deprecations = deprecations,
          envVars = envVars
        )

      case Failure(e) =>
        log.error(
          "Configuration verification for [{}] failed with [{} - {}]",
          configFileName,
          e.getClass.getSimpleName,
          e.getMessage
        )
    }
  }

  private[config] def parseDeprecations(content: Seq[String]): Seq[Deprecation] =
    content
      .sliding(size = 2)
      .flatMap { case maybeComment :: maybeConfig :: Nil =>
        maybeConfig match {
          case parameterWithEnvVar(parameter, envVar) =>
            maybeComment match {
              case deprecatedWarningWithReplacement(deprecatedReplacement) =>
                Some(
                  Deprecation(
                    parameter = parameter,
                    deprecatedEnvVar = envVar,
                    replacementEnvVar = Some(deprecatedReplacement)
                  )
                )

              case deprecatedWarning() =>
                Some(
                  Deprecation(
                    parameter = parameter,
                    deprecatedEnvVar = envVar,
                    replacementEnvVar = None
                  )
                )

              case _ =>
                // not a deprecation warning
                None
            }

          case _ =>
            // not a parameter with an environment variable
            None
        }
      }
      .toSeq

  private[config] def processDeprecations(deprecations: Seq[Deprecation], envVars: Set[String])(implicit log: Logger): Unit =
    deprecations
      .filter(deprecation => envVars.contains(deprecation.deprecatedEnvVar))
      .foreach {
        case Deprecation(parameter, deprecatedEnvVar, Some(replacementEnvVar)) =>
          log.warn(
            "Environment variable [{}] for parameter [{}] is deprecated; use [{}] instead",
            deprecatedEnvVar,
            parameter,
            replacementEnvVar
          )

        case Deprecation(parameter, deprecatedEnvVar, None) =>
          log.warn(
            "Environment variable [{}] for parameter [{}] is deprecated",
            deprecatedEnvVar,
            parameter
          )
      }

  private[config] final case class Deprecation(
    parameter: String,
    deprecatedEnvVar: String,
    replacementEnvVar: Option[String]
  )

  /**
    * Matches
    * <ul>
    *   <li>"`# deprecated`"</li>
    *   <li>"`// deprecated`"</li>
    * </ul>
    * with arbitrary whitespace before and after the word "`deprecated`"
    */
  private val deprecatedWarning: Regex = "^(?:#|//)\\s*deprecated\\s*".r

  /**
    * Matches:
    * <ul>
    *   <li>"`# deprecated, use XYZ`"</li>
    *   <li>"`// deprecated, use XYZ`"</li>
    * </ul>
    * with arbitrary whitespace before and after the various tokens/words;
    * extracts the replacement env var (ex: "`XYZ`")
    */
  private val deprecatedWarningWithReplacement: Regex = "^(?:#|//)\\s*deprecated\\s*,\\s*use\\s*(\\w+).*".r

  /**
    * Matches:
    * <ul>
    *   <li>"`param-name = ${?ENV_VAR_NAME}`"</li>
    * </ul>
    * with arbitrary whitespace before and after the various tokens/words;
    * extracts the parameter name (ex: "`param-name`") and the associated env var (ex: "`ENV_VAR_NAME`")
    */
  private val parameterWithEnvVar: Regex = "(\\w+)\\s*=\\s*\\$\\{\\?(\\w+)\\}.*".r
}
