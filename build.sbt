import com.typesafe.tools.mima.core.*
import sbt.Keys.*

val libraryName: String = "layers"

name := libraryName

ThisBuild / scalaVersion       := "2.13.18"
ThisBuild / crossScalaVersions := Seq("2.13.18", "3.3.9")

ThisBuild / organization  := "io.github.sndnv"
ThisBuild / homepage      := Some(uri("https://github.com/sndnv/layers"))
ThisBuild / licenses      := List(License.Apache2)
ThisBuild / versionScheme := Some("semver-spec")
ThisBuild / developers    := List(
  Developer(
    id = "sndnv",
    name = "Angel Sanadinov",
    email = "angel.sanadinov@gmail.com",
    url = uri("https://github.com/sndnv")
  )
)

lazy val versions = (
  // pekko
  pekko = "2.0.0",
  pekkoHttp = "2.0.0",
  pekkoJson = "4.0.0",

  // persistence
  slick = "3.6.1",
  h2 = "2.5.252",

  // telemetry
  openTelemetry = "1.68.0",
  openTelemetryPrometheus = "1.68.0-alpha",
  prometheus = "1.10.0",

  // testing
  scalaCheck = "1.20.0",
  scalaTest = "3.2.20",
  mockito = "5.24.0",
  jimfs = "1.3.2",

  // misc
  playJson = "3.0.6",
  jose4j = "0.9.7",
  logback = "1.6.7"
)

lazy val root = project
  .in(file("."))
  .settings(
    commonSettings,
    Seq(
      name                 := s"$libraryName-root",
      publish / skip       := true,
      publishLocal / skip  := true,
      mimaFailOnNoPrevious := false
    )
  )
  .aggregate(testing, lib)

lazy val lib = (project in file("./lib"))
  .settings(commonSettings)
  .settings(
    name                  := libraryName,
    description           := "Generic components for building services",
    libraryDependencies ++= Seq(
      "org.apache.pekko"     %% "pekko-actor-typed"                 % versions.pekko                   % Provided,
      "org.apache.pekko"     %% "pekko-stream"                      % versions.pekko                   % Provided,
      "org.apache.pekko"     %% "pekko-slf4j"                       % versions.pekko                   % Provided,
      "org.apache.pekko"     %% "pekko-http"                        % versions.pekkoHttp               % Provided,
      "org.apache.pekko"     %% "pekko-http-core"                   % versions.pekkoHttp               % Provided,
      "org.playframework"    %% "play-json"                         % versions.playJson                % Provided,
      "com.github.pjfanning" %% "pekko-http-play-json"              % versions.pekkoJson               % Provided,
      "org.bitbucket.b_c"     % "jose4j"                            % versions.jose4j                  % Provided,
      "io.opentelemetry"      % "opentelemetry-api"                 % versions.openTelemetry           % Provided,
      "io.opentelemetry"      % "opentelemetry-sdk"                 % versions.openTelemetry           % Provided,
      "io.opentelemetry"      % "opentelemetry-exporter-prometheus" % versions.openTelemetryPrometheus % Provided,
      "io.prometheus"         % "prometheus-metrics-model"          % versions.prometheus              % Provided,
      "com.typesafe.slick"   %% "slick"                             % versions.slick                   % Test,
      "com.h2database"        % "h2"                                % versions.h2                      % Test,
      "io.prometheus"         % "prometheus-metrics-core"           % versions.prometheus              % Test,
      "org.scalacheck"       %% "scalacheck"                        % versions.scalaCheck              % Test,
      "org.scalatest"        %% "scalatest"                         % versions.scalaTest               % Test,
      "org.apache.pekko"     %% "pekko-testkit"                     % versions.pekko                   % Test,
      "org.apache.pekko"     %% "pekko-stream-testkit"              % versions.pekko                   % Test,
      "org.apache.pekko"     %% "pekko-http-testkit"                % versions.pekkoHttp               % Test,
      "org.mockito"           % "mockito-core"                      % versions.mockito                 % Test,
      "com.google.jimfs"      % "jimfs"                             % versions.jimfs                   % Test,
      "ch.qos.logback"        % "logback-classic"                   % versions.logback                 % Test
    ),
    mimaPreviousArtifacts := previousStableVersion.value
      .map(version => organization.value %% moduleName.value % version)
      .toSet,
    mimaFailOnProblem     := sys.env.getOrElse("MIMA_FAIL_ON_PROBLEM", "true").toBoolean,
    mimaBinaryIssueFilters ++= Seq(
      ProblemFilters.exclude[Problem]("io.github.sndnv.layers.service.components.internal.*")
    )
  )
  .dependsOn(testing % "test->compile")

lazy val testing = (project in file("./testing"))
  .settings(commonSettings)
  .settings(
    name                  := s"$libraryName-testing",
    description           := "Helper classes and functions for testing",
    exportJars            := false,
    libraryDependencies ++= Seq(
      "org.apache.pekko"   %% "pekko-actor-typed" % versions.pekko      % Provided,
      "org.apache.pekko"   %% "pekko-stream"      % versions.pekko      % Provided,
      "org.scalacheck"     %% "scalacheck"        % versions.scalaCheck % Provided,
      "org.scalatest"      %% "scalatest"         % versions.scalaTest  % Provided,
      "com.google.jimfs"    % "jimfs"             % versions.jimfs      % Provided,
      "com.typesafe.slick" %% "slick"             % versions.slick      % Provided,
      "com.h2database"      % "h2"                % versions.h2         % Provided
    ),
    mimaPreviousArtifacts := previousStableVersion.value
      .map(version => organization.value %% moduleName.value % version)
      .toSet,
    mimaFailOnProblem     := sys.env.getOrElse("MIMA_FAIL_ON_PROBLEM", "true").toBoolean
  )

lazy val excludedWarts = Seq(
  Wart.Any // too many false positives; more info - https://github.com/wartremover/wartremover/issues/454
)

lazy val commonSettings = Seq(
  Test / logBuffered       := false,
  Test / parallelExecution := false,
  coverageDataDir          := (Compile / classDirectory).value,
  bomOutputPath            := target.value.getPath,
  includeBomSerialNumber   := true,
  includeBomTimestamp      := true,
  Compile / compile / wartremoverWarnings ++= Warts.unsafe.filterNot(excludedWarts.contains),
  scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((3, _)) =>
        Seq(
          "-encoding",
          "UTF-8",
          "-unchecked",
          "-deprecation",
          "-feature",
          s"-P:wartremover:excluded:${(Compile / sourceManaged).value}"
        )

      case _ =>
        Seq(
          "-encoding",
          "UTF-8",
          "-unchecked",
          "-deprecation",
          "-feature",
          "-Xcheckinit",
          "-Ywarn-dead-code",
          "-Ywarn-numeric-widen",
          "-Ywarn-unused",
          "-Ywarn-extra-implicit",
          "-Ywarn-unused:implicits",
          "-Xlint:constant",
          "-Xlint:delayedinit-select",
          "-Xlint:doc-detached",
          "-Xlint:inaccessible",
          "-Xlint:infer-any",
          s"-P:wartremover:excluded:${(Compile / sourceManaged).value}",
          "-Xsource:3"
        )
    }
  }
)

Global / concurrentRestrictions += Tags.limit(
  Tags.Test,
  sys.env.getOrElse("CI", "").trim.toLowerCase match {
    case "true" => 1
    case _      => 2
  }
)

addCommandAlias(
  name = "prepare",
  value = "; clean; compile; Test/compile"
)

addCommandAlias(
  name = "check",
  value = "; dependencyUpdates; scalafmtSbtCheck; scalafmtCheck; Test/scalafmtCheck; mimaReportBinaryIssues"
)

addCommandAlias(
  name = "qa",
  value = "; prepare; check; coverage; testFull; coverageReport; coverageAggregate"
)
