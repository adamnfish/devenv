package com.gu.devenv.e2e.testutils

import com.gu.devenv.e2e.testutils.DevcontainerRunner
import com.gu.devenv.{Devenv, GenerateResult}
import org.scalatest.{BeforeAndAfterEach, Suite, Tag}

import java.nio.file.{Files, Path, StandardCopyOption}

/** Tag for tests that require Docker and are slow.
  *
  * Use this to filter tests when you want to skip the slow container tests:
  *
  * sbt "endToEndTests/testOnly -- -l com.gu.devenv.e2e.ContainerTest"
  *
  * Or to run only container tests:
  *
  * sbt "endToEndTests/testOnly -- -n com.gu.devenv.e2e.ContainerTest"
  */
object ContainerTest extends Tag("com.gu.devenv.e2e.ContainerTest")

/** Test helper trait that manages workspace setup, devcontainer generation, and cleanup.
  *
  * Provides utilities for:
  *   - Copying fixture directories to temporary workspaces
  *   - Running devenv generate to create devcontainer.json files (using the core library directly)
  *   - Managing container lifecycle (up/down)
  */
trait DevcontainerTestSupport extends BeforeAndAfterEach { self: Suite =>

  // Path to the fixtures directory
  protected lazy val fixturesDir: Path = {
    // Find the project root by looking for build.sbt
    var current = Path.of(System.getProperty("user.dir"))
    while (current != null && !Files.exists(current.resolve("build.sbt")))
      current = current.getParent
    require(current != null, "Could not find project root (build.sbt)")
    current.resolve("e2e-tests/src/test/resources/fixtures")
  }

  protected var currentWorkspace: Option[Path]            = None
  protected var currentRunner: Option[DevcontainerRunner] = None

  /** Copy a fixture to a temporary directory and return the path */
  protected def setupWorkspace(fixtureName: String): Path = {
    val fixtureDir = fixturesDir.resolve(fixtureName)
    require(Files.isDirectory(fixtureDir), s"Fixture not found: $fixtureDir")

    val tempDir = Files.createTempDirectory(s"devenv-e2e-$fixtureName-")
    copyDirectory(fixtureDir, tempDir)

    currentWorkspace = Some(tempDir)
    tempDir
  }

  /** Run devenv generate in the given workspace using the core library directly.
    *
    * This avoids needing a staged CLI binary and ensures we're always testing against the current
    * source code.
    */
  protected def runDevenvGenerate(workspace: Path): Either[String, GenerateResult.Success] = {
    val devcontainerDir = workspace.resolve(".devcontainer")
    // This user config path won't exist, but we aren't using it in these e2e tests
    val userConfigPath = workspace.resolve(".config/devenv/devenv.yaml")

    Devenv.generate(devcontainerDir, userConfigPath) match {
      case scala.util.Success(result: GenerateResult.Success) =>
        Right(result)
      case scala.util.Success(GenerateResult.NotInitialized) =>
        Left("devenv not initialized: .devcontainer/devenv.yaml not found")
      case scala.util.Success(GenerateResult.ConfigNotCustomized) =>
        Left("devenv.yaml has not been customized (still using placeholder name)")
      case scala.util.Failure(err) =>
        Left(s"Generation failed: ${err.getMessage}")
    }
  }

  /** Creates a runner for the workspace and starts the container */
  protected def startContainer(workspace: Path): Either[String, DevcontainerRunner] = {
    val runner = new DevcontainerRunner(workspace)
    currentRunner = Some(runner)

    // First, run devenv generate
    runDevenvGenerate(workspace) match {
      case Left(error) =>
        return Left(s"Failed to generate devcontainer.json: $error")
      case Right(_) => // continue
    }

    // Build the container
    val buildResult = runner.build()
    if (buildResult.failed) {
      return Left(s"Failed to build container: ${buildResult.combinedOutput}")
    }

    // Start the container
    runner.up().map(_ => runner)
  }

  override protected def afterEach(): Unit = {
    // Stop and clean up container
    currentRunner.foreach { runner =>
      runner.down()
      runner.cleanupVolumes()
    }
    currentRunner = None

    // Clean up workspace directory
    currentWorkspace.foreach { workspace =>
      deleteDirectory(workspace)
    }
    currentWorkspace = None

    super.afterEach()
  }

  private def copyDirectory(source: Path, target: Path): Unit =
    Files.walk(source).forEach { sourcePath =>
      val targetPath = target.resolve(source.relativize(sourcePath))
      if (Files.isDirectory(sourcePath)) {
        Files.createDirectories(targetPath): Unit
      } else {
        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING): Unit
      }
    }

  private def deleteDirectory(path: Path): Unit =
    if (Files.exists(path)) {
      Files
        .walk(path)
        .sorted(java.util.Comparator.reverseOrder())
        .forEach(Files.delete)
    }
}
