package com.gu.devenv.e2e.verifiers

import com.gu.devenv.e2e.testutils.DevcontainerRunner

/** Verifies that docker-in-docker module has been configured correctly.
  *
  * Checks:
  *   - docker daemon is running
  *   - docker compose is available
  *   - Can run a simple docker compose up
  */
object DockerInDockerVerifier {
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
