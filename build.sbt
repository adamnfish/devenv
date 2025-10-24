ThisBuild / scalaVersion := "3.3.6" // latest LTS
ThisBuild / organization := "com.gu"
ThisBuild / scalacOptions ++= Seq(
  "-encoding",
  "utf8",
  "-feature",
  "-unchecked",
  "-deprecation",
  "-Wunused:all",
  "-Wvalue-discard",
  "-Xfatal-warnings"
)
ThisBuild / Test / parallelExecution := true
ThisBuild / Test / testOptions += Tests.Argument("-oD") // Show test durations

val circeVersion = "0.14.15"

lazy val root = (project in file("."))
  .settings(
    name := "devenv"
  )
  .aggregate(cli, core)

lazy val cli = (project in file("cli"))
  .settings(
    fork := true,
    // Fast startup JVM options (used when running via `sbt run`)
    javaOptions ++= Seq(
      "-XX:+TieredCompilation",  // Enable tiered compilation
      "-XX:TieredStopAtLevel=1", // Stop at C1 compiler (faster startup, less optimization)
      "-Xshare:auto",            // Use class data sharing if available
      "-XX:+UseSerialGC",        // Faster GC for short-lived processes
      "-Xms64m",                 // Small initial heap
      "-Xmx512m"                 // Reasonable max heap
    )
  )
  .dependsOn(core)

lazy val core = project
  .in(file("core"))
  .settings(
    libraryDependencies ++= Seq(
      "io.circe"      %% "circe-core"           % circeVersion,
      "io.circe"      %% "circe-generic"        % circeVersion,
      "io.circe"      %% "circe-parser"         % circeVersion,
      "io.circe"      %% "circe-generic-extras" % "0.14.5-RC1",
      "io.circe"      %% "circe-yaml-scalayaml" % "0.16.1",
      "com.lihaoyi"   %% "fansi"                % "0.5.1",
      "org.typelevel" %% "cats-core"            % "2.13.0",
      "org.scalatest" %% "scalatest"            % "3.2.19" % Test
    )
  )
