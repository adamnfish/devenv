package com.gu.devenv.e2e

import com.gu.devenv.e2e.testutils.DevcontainerRunner

/** Trait for module-specific verification logic */
trait ModuleAssertion {
  def name: String
  def verify(runner: DevcontainerRunner): Either[String, Unit]
}

/** Verifies that apt-updates module has run successfully.
  *
  * Checks that no packages are upgradeable after the postCreateCommand has run.
  */
object AptUpdatesAssertion extends ModuleAssertion {
  val name = "apt-updates"

  def verify(runner: DevcontainerRunner): Either[String, Unit] = {
    // Check that there are no upgradeable packages
    // apt-get -s upgrade simulates an upgrade and we count installable packages
    val result = runner.exec(
      """apt-get update -qq 2>/dev/null && apt list --upgradeable 2>/dev/null | grep -c upgradable || echo 0"""
    )

    if (result.failed) {
      Left(s"Failed to check for upgradeable packages: ${result.combinedOutput}")
    } else {
      val upgradeableCount = result.stdout.trim.toIntOption.getOrElse(-1)
      if (upgradeableCount == 0) {
        Right(())
      } else {
        Left(s"Expected 0 upgradeable packages but found $upgradeableCount")
      }
    }
  }
}

/** Verifies that mise module has been installed and configured correctly.
  *
  * Checks:
  * - mise binary is installed
  * - mise doctor runs successfully
  * - Tools from .mise.toml are available on PATH via shims
  */
object MiseAssertion extends ModuleAssertion {
  val name = "mise"

  // mise is installed to ~/.local/bin which may not be on PATH in exec sessions
  private val miseBin = "$HOME/.local/bin/mise"

  def verify(runner: DevcontainerRunner): Either[String, Unit] =
    for {
      _ <- checkMiseInstalled(runner)
      _ <- checkMiseDoctor(runner)
      _ <- checkMiseToolsAvailable(runner)
    } yield ()

  private def checkMiseInstalled(runner: DevcontainerRunner): Either[String, Unit] = {
    val result = runner.exec(s"$miseBin --version")
    if (result.succeeded) Right(())
    else Left(s"mise is not installed: ${result.combinedOutput}")
  }

  private def checkMiseDoctor(runner: DevcontainerRunner): Either[String, Unit] = {
    // mise doctor may return non-zero for warnings, so we just check it runs
    // and produces output containing expected sections
    val result = runner.exec(s"$miseBin doctor")
    if (result.stdout.contains("toolset:") || result.stdout.contains("dirs:")) {
      Right(())
    } else {
      Left(s"mise doctor did not produce expected output: ${result.combinedOutput}")
    }
  }

  private def checkMiseToolsAvailable(runner: DevcontainerRunner): Either[String, Unit] = {
    // Check that node is available via mise shims (our test fixture installs node 22)
    // The shims directory is on PATH via remoteEnv
    val result = runner.exec("node --version")
    if (result.succeeded && result.stdout.contains("v22")) {
      Right(())
    } else if (result.succeeded) {
      Left(s"node is available but wrong version: ${result.stdout}")
    } else {
      Left(s"node is not available via mise shims: ${result.combinedOutput}")
    }
  }
}

/** Verifies that docker-in-docker module has been configured correctly.
  *
  * Checks:
  * - docker daemon is running
  * - docker compose is available
  * - Can run a simple docker compose up
  */
object DockerInDockerAssertion extends ModuleAssertion {
  val name = "docker-in-docker"

  def verify(runner: DevcontainerRunner): Either[String, Unit] =
    for {
      _ <- checkDockerRunning(runner)
      _ <- checkDockerCompose(runner)
      _ <- checkDockerComposeWorks(runner)
    } yield ()

  private def checkDockerRunning(runner: DevcontainerRunner): Either[String, Unit] = {
    val result = runner.exec("docker ps")
    if (result.succeeded) Right(())
    else Left(s"Docker is not running: ${result.combinedOutput}")
  }

  private def checkDockerCompose(runner: DevcontainerRunner): Either[String, Unit] = {
    val result = runner.exec("docker compose version")
    if (result.succeeded) Right(())
    else Left(s"Docker Compose is not available: ${result.combinedOutput}")
  }

  private def checkDockerComposeWorks(runner: DevcontainerRunner): Either[String, Unit] = {
    // The workspace is mounted under /workspaces/<dir-name>
    // We find it dynamically since the temp directory name varies
    val findWorkspace = runner.exec("ls -d /workspaces/*/")
    if (findWorkspace.failed) {
      return Left(s"Could not find workspace directory: ${findWorkspace.combinedOutput}")
    }
    val workspaceDir = findWorkspace.stdout.trim.split("\n").headOption.getOrElse("")
    if (workspaceDir.isEmpty) {
      return Left("No workspace directory found under /workspaces/")
    }

    // Run docker compose with the test docker-compose.yaml (hello-world service)
    val result = runner.exec(s"cd $workspaceDir && docker compose up -d")
    if (result.succeeded) {
      // Clean up
      runner.exec(s"cd $workspaceDir && docker compose down")
      Right(())
    } else {
      Left(s"Docker Compose failed to start services: ${result.combinedOutput}")
    }
  }
}

