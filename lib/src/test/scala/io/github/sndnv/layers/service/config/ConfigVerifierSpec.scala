package io.github.sndnv.layers.service.config

import java.io.File

import scala.util.Success

import io.github.sndnv.layers.testing.UnitSpec
import org.mockito.scalatest.AsyncMockitoSugar
import org.slf4j.Logger

class ConfigVerifierSpec extends UnitSpec with AsyncMockitoSugar {
  "A ConfigVerifier" should "support loading raw config file content" in {
    val actualContent = ConfigVerifier.loadContent(
      file = new File(getClass.getClassLoader.getResource("verification-test.conf").getFile)
    )

    actualContent should be(Success(configContent))
  }

  it should "support parsing environment variable deprecations from raw config file content" in {
    val actual = ConfigVerifier.parseDeprecations(content = configContent)

    actual.sortBy(_.parameter).toList match {
      case first :: second :: Nil =>
        first.parameter should be("f")
        first.deprecatedEnvVar should be("A_B_F_OLD")
        first.replacementEnvVar should be(Some("A_B_F_NEW"))

        second.parameter should be("g")
        second.deprecatedEnvVar should be("A_B_G_OLD")
        second.replacementEnvVar should be(None)

      case other =>
        fail(s"Unexpected result received: [$other]")
    }
  }

  it should "support processing environment variable deprecations" in {
    implicit val logger: Logger = mock[Logger]

    ConfigVerifier.processDeprecations(
      deprecations = Seq(
        ConfigVerifier.Deprecation(
          parameter = "param-a",
          deprecatedEnvVar = "TEST_ENV_VAR_A",
          replacementEnvVar = Some("TEST_ENV_VAR_A_REPLACEMENT")
        ),
        ConfigVerifier.Deprecation(
          parameter = "param-b",
          deprecatedEnvVar = "TEST_ENV_VAR_A",
          replacementEnvVar = None
        ),
        ConfigVerifier.Deprecation(
          parameter = "param-c",
          deprecatedEnvVar = "TEST_ENV_VAR_B",
          replacementEnvVar = Some("TEST_ENV_VAR_B_REPLACEMENT")
        ),
        ConfigVerifier.Deprecation(
          parameter = "param-d",
          deprecatedEnvVar = "TEST_ENV_VAR_C",
          replacementEnvVar = None
        )
      ),
      envVars = Set("TEST_ENV_VAR_A", "TEST_ENV_VAR_C")
    )

    verify(logger).warn(
      "Environment variable [{}] for parameter [{}] is deprecated; use [{}] instead",
      "TEST_ENV_VAR_A",
      "param-a",
      "TEST_ENV_VAR_A_REPLACEMENT"
    )

    verify(logger).warn(
      "Environment variable [{}] for parameter [{}] is deprecated",
      "TEST_ENV_VAR_A",
      "param-b"
    )

    verify(logger).warn(
      "Environment variable [{}] for parameter [{}] is deprecated",
      "TEST_ENV_VAR_C",
      "param-d"
    )

    succeed
  }

  it should "support verifying config files (as resource, without file extension)" in {
    implicit val logger: Logger = mock[Logger]

    ConfigVerifier.verify(configBasename = "verification-test", envVars = Set("A_B_F_OLD"))

    verify(logger).warn(
      "Environment variable [{}] for parameter [{}] is deprecated; use [{}] instead",
      "A_B_F_OLD",
      "f",
      "A_B_F_NEW"
    )

    succeed
  }

  it should "support verifying config files (as resource, with file extension)" in {
    implicit val logger: Logger = mock[Logger]

    ConfigVerifier.verify(configBasename = "verification-test.conf", envVars = Set("A_B_F_OLD"))

    verify(logger).warn(
      "Environment variable [{}] for parameter [{}] is deprecated; use [{}] instead",
      "A_B_F_OLD",
      "f",
      "A_B_F_NEW"
    )

    succeed
  }

  it should "support verifying config files (from file system, without file extension)" in {
    implicit val logger: Logger = mock[Logger]

    ConfigVerifier.verify(configBasename = "lib/src/test/resources/verification-test", envVars = Set("A_B_F_OLD"))

    verify(logger).warn(
      "Environment variable [{}] for parameter [{}] is deprecated; use [{}] instead",
      "A_B_F_OLD",
      "f",
      "A_B_F_NEW"
    )

    succeed
  }

  it should "support verifying config files (from file system, with file extension)" in {
    implicit val logger: Logger = mock[Logger]

    ConfigVerifier.verify(configBasename = "lib/src/test/resources/verification-test.conf", envVars = Set("A_B_F_OLD"))

    verify(logger).warn(
      "Environment variable [{}] for parameter [{}] is deprecated; use [{}] instead",
      "A_B_F_OLD",
      "f",
      "A_B_F_NEW"
    )

    succeed
  }

  it should "handle failures during config file verification" in {
    implicit val logger: Logger = mock[Logger]

    ConfigVerifier.verify(configBasename = "missing.conf")

    verify(logger).error(
      eqTo("Configuration verification for [{}] failed with [{} - {}]"),
      any[String],
      eqTo("FileNotFoundException"),
      eqTo("missing.conf (No such file or directory)")
    )

    succeed
  }

  it should "support verifying the default confing file" in {
    implicit val logger: Logger = mock[Logger]

    ConfigVerifier.verify()

    verify(logger, never).warn(
      eqTo("Environment variable [{}] for parameter [{}] is deprecated"),
      any[String],
      any[String]
    )

    verify(logger, never).warn(
      eqTo("Environment variable [{}] for parameter [{}] is deprecated; use [{}] instead"),
      any[String],
      any[String],
      any[String]
    )

    verify(logger, never).error(
      eqTo("Configuration verification for [{}] failed with [{} - {}]"),
      any[String],
      any[String],
      any[String]
    )

    succeed
  }

  private val configContent: Seq[String] = Seq(
    """a {""",
    """b {""",
    """c = "d"""",
    """c = ${?A_B_C}""",
    """e = 42""",
    """e = ${?A_B_E}""",
    """f = """"",
    """# deprecated, use A_B_F_NEW""",
    """f = ${?A_B_F_OLD}""",
    """f = ${?A_B_F_NEW}""",
    """g = """"",
    """// deprecated""",
    """g = ${?A_B_G_OLD}""",
    """g = ${?A_B_G_NEW}""",
    """}""",
    """}"""
  )
}
