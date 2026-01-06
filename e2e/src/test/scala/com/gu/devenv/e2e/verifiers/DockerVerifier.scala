package com.gu.devenv.e2e.verifiers

import com.gu.devenv.e2e.testutils.CommandRunner

/** Utility to check Docker availability for E2E tests.
  *
  * This checks that Docker is available on the host machine before running
  * devcontainer tests, which require Docker to create and run containers.
  */
object DockerVerifier {

  /** Checks if Docker is installed, running, and working properly.
    *
    * @return
    *   Right(()) if Docker is available and working, Left(error message) otherwise
    */
  def verify(): Either[String, Unit] =
    for {
      _ <- checkDockerInstalled()
      _ <- checkDockerDaemonRunning()
      _ <- checkDockerCanRunContainers()
    } yield ()

  private def checkDockerInstalled(): Either[String, Unit] = {
    val dockerExists = CommandRunner.run("command -v docker")
    if (dockerExists.succeeded) {
      Right(())
    } else {
      Left("Docker is not installed or not on PATH. Please install Docker Desktop or Docker Engine.")
    }
  }

  private def checkDockerDaemonRunning(): Either[String, Unit] = {
    val dockerInfo = CommandRunner.run("docker info")
    if (dockerInfo.succeeded) {
      Right(())
    } else {
      Left("Docker daemon is not running. Please start Docker Desktop.")
    }
  }

  private def checkDockerCanRunContainers(): Either[String, Unit] = {
    val dockerRun = CommandRunner.run("docker run --rm hello-world")
    if (dockerRun.succeeded) {
      Right(())
    } else {
      Left(
        """Docker is installed but cannot run containers.
          |This might be a permissions issue or Docker daemon problem.
          |""".stripMargin
      )
    }
  }
}

