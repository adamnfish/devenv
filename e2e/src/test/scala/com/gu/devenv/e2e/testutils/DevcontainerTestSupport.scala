package com.gu.devenv.e2e.testutils

import com.gu.devenv.e2e.testutils.DevcontainerRunner
import com.gu.devenv.{Devenv, GenerateResult}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite, Tag}

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
  *
  * Before running any tests, checks that Docker is available and working.
  */
trait DevcontainerTestSupport extends BeforeAndAfterEach with BeforeAndAfterAll { self: Suite =>
  // the root test fixtures directory, each fixture is found under this
  protected lazy val fixturesDir: Path = {
    val resource = getClass.getResource("/fixtures")
    require(resource != null, "Could not find fixtures directory in resources")
    Path.of(resource.toURI)
  }

  // user config fixture directory with empty devenv.yaml
  protected lazy val userConfigFixtureDir: Path = {
    val resource = getClass.getResource("/fixtures/user-config/.config/devenv")
    require(resource != null, "Could not find user-config fixture directory in resources")
    Path.of(resource.toURI)
  }

  protected var currentWorkspace: Option[Path]            = None
  protected var currentRunner: Option[DevcontainerRunner] = None

  // Check Docker availability and fixtures before running any tests in this suite
  override protected def beforeAll(): Unit = {
    super.beforeAll()

    // Check that we can find the fixtures directory
    require(
      Files.isDirectory(fixturesDir),
      s"Fixtures directory not found at: $fixturesDir. This indicates a problem with the test resources setup - ensure e2e/src/test/resources/fixtures exists and is properly configured."
    )

    DockerChecker.checkDockerAvailable() match {
      case Left(error) =>
        throw new RuntimeException(
          s"""Docker is not available. Cannot run container tests.
             |
             |$error
             |
             |Please ensure Docker Desktop is installed and running before running E2E tests.
             |----------------------------------------------------------------------------
             |""".stripMargin
        )
      case Right(_) => // Docker is available, proceed with tests
    }
  }

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
    // Pass the directory containing devenv.yaml (Filesystem.resolveUserConfigPaths will append the filename)
    val userConfigPath = userConfigFixtureDir

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

    runner
      .up()             // tries to start the container
      .map(_ => runner) // return the runner on success
  }

  // clean up container and temporary directory
  override protected def afterEach(): Unit = {
    // Stop and clean up container
    currentRunner.foreach { runner =>
      runner.down()
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
        .sorted(Ordering[Path].reverse)
        .forEach(Files.delete(_))
    }
}
