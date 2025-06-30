import sbt.Keys.*

val libraryName: String = "layers"

name := libraryName

ThisBuild / scalaVersion := "2.13.16"

ThisBuild / organization  := "io.github.sndnv"
ThisBuild / homepage      := Some(url("https://github.com/sndnv/layers"))
ThisBuild / licenses      := List(License.Apache2)
ThisBuild / versionScheme := Some("semver-spec")
ThisBuild / developers    := List(
  Developer(
    id = "sndnv",
    name = "Angel Sanadinov",
    email = "angel.sanadinov@gmail.com",
    url = url("https://github.com/sndnv")
  )
)

lazy val versions = new {
  // pekko
  val pekko         = "1.1.4"
  val pekkoHttp     = "1.2.0"
  val pekkoHttpCors = "1.2.0"
  val pekkoJson     = "3.2.1"

  // persistence
  val slick = "3.6.1"
  val h2    = "2.3.232"

  // telemetry
  val openTelemetry           = "1.51.0"
  val openTelemetryPrometheus = "1.51.0-alpha"
  val prometheus              = "0.16.0"

  // testing
  val scalaCheck    = "1.18.1"
  val scalaTest     = "3.2.19"
  val wiremock      = "3.0.1"
  val mockito       = "2.0.0"
  val mockitoInline = "5.2.0"
  val jimfs         = "1.3.0"

  // misc
  val playJson = "2.10.6"
  val jose4j   = "0.9.6"
  val logback  = "1.5.18"
}

lazy val root = project
  .in(file("."))
  .settings(
    commonSettings,
    Seq(
      publish / skip      := true,
      publishLocal / skip := true
    )
  )
  .aggregate(testing, lib)

lazy val lib = (project in file("./lib"))
  .settings(commonSettings)
  .settings(
    name := libraryName,
    libraryDependencies ++= Seq(
      "org.apache.pekko"      %% "pekko-actor-typed"                 % versions.pekko                   % Provided,
      "org.apache.pekko"      %% "pekko-stream"                      % versions.pekko                   % Provided,
      "org.apache.pekko"      %% "pekko-slf4j"                       % versions.pekko                   % Provided,
      "org.apache.pekko"      %% "pekko-http"                        % versions.pekkoHttp               % Provided,
      "org.apache.pekko"      %% "pekko-http-core"                   % versions.pekkoHttp               % Provided,
      "com.typesafe.play"     %% "play-json"                         % versions.playJson                % Provided,
      "com.github.pjfanning"  %% "pekko-http-play-json"              % versions.pekkoJson               % Provided,
      "org.bitbucket.b_c"      % "jose4j"                            % versions.jose4j                  % Provided,
      "io.opentelemetry"       % "opentelemetry-api"                 % versions.openTelemetry           % Provided,
      "io.opentelemetry"       % "opentelemetry-sdk"                 % versions.openTelemetry           % Provided,
      "io.opentelemetry"       % "opentelemetry-exporter-prometheus" % versions.openTelemetryPrometheus % Provided,
      "io.prometheus"          % "simpleclient"                      % versions.prometheus              % Provided,
      "com.typesafe.slick"    %% "slick"                             % versions.slick                   % Test,
      "com.h2database"         % "h2"                                % versions.h2                      % Test,
      "org.scalacheck"        %% "scalacheck"                        % versions.scalaCheck              % Test,
      "org.scalatest"         %% "scalatest"                         % versions.scalaTest               % Test,
      "org.apache.pekko"      %% "pekko-testkit"                     % versions.pekko                   % Test,
      "org.apache.pekko"      %% "pekko-stream-testkit"              % versions.pekko                   % Test,
      "org.apache.pekko"      %% "pekko-http-testkit"                % versions.pekkoHttp               % Test,
      "com.github.tomakehurst" % "wiremock-jre8"                     % versions.wiremock                % Test,
      "org.mockito"           %% "mockito-scala"                     % versions.mockito                 % Test,
      "org.mockito"           %% "mockito-scala-scalatest"           % versions.mockito                 % Test,
      "org.mockito"            % "mockito-inline"                    % versions.mockitoInline           % Test,
      "com.google.jimfs"       % "jimfs"                             % versions.jimfs                   % Test,
      "ch.qos.logback"         % "logback-classic"                   % versions.logback                 % Test
    )
  )
  .dependsOn(testing % "test->compile")

lazy val testing = (project in file("./testing"))
  .settings(commonSettings)
  .settings(
    name := s"$libraryName-testing",
    libraryDependencies ++= Seq(
      "org.apache.pekko"   %% "pekko-actor-typed" % versions.pekko      % Provided,
      "org.apache.pekko"   %% "pekko-stream"      % versions.pekko      % Provided,
      "org.scalacheck"     %% "scalacheck"        % versions.scalaCheck % Provided,
      "org.scalatest"      %% "scalatest"         % versions.scalaTest  % Provided,
      "com.google.jimfs"    % "jimfs"             % versions.jimfs      % Provided,
      "com.typesafe.slick" %% "slick"             % versions.slick      % Provided,
      "com.h2database"      % "h2"                % versions.h2         % Provided
    )
  )

lazy val excludedWarts = Seq(
  Wart.Any // too many false positives; more info - https://github.com/wartremover/wartremover/issues/454
)

lazy val commonSettings = Seq(
  Test / logBuffered       := false,
  Test / parallelExecution := false,
  Compile / compile / wartremoverWarnings ++= Warts.unsafe.filterNot(excludedWarts.contains),
  scalacOptions ++= Seq(
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
    s"-P:wartremover:excluded:${(Compile / sourceManaged).value}"
  )
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
  value = "; dependencyUpdates; scalafmtSbtCheck; scalafmtCheck; Test/scalafmtCheck"
)

addCommandAlias(
  name = "qa",
  value = "; prepare; check; coverage; test; coverageReport; coverageAggregate"
)
