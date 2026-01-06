package com.gu.devenv.e2e.testutils

/** Utility to check Docker availability for E2E tests. */
object DockerChecker {

  /** Checks if Docker is installed, running, and working properly.
    *
    * @return
    *   Right(()) if Docker is available and working, Left(error message) otherwise
    */
  def checkDockerAvailable(): Either[String, Unit] =
    for {
      // Check if docker command exists
      _ <- {
        val dockerExists = CommandRunner.run("command -v docker")
        Either.cond(
          dockerExists.succeeded,
          (),
          "Docker is not installed or not on PATH. Please install Docker Desktop or Docker Engine."
        )
      }
      // Check if Docker daemon is running by trying to connect
      _ <- {
        val dockerInfo = CommandRunner.run("docker info")
        Either.cond(
          dockerInfo.succeeded,
          (),
          "Docker daemon is not running. Please start Docker Desktop."
        )
      }
      // Try to run a simple container to verify everything works
      _ <- {
        val dockerRun = CommandRunner.run("docker run --rm hello-world")
        Either.cond(
          dockerRun.succeeded,
          (),
          """Docker is installed but cannot run containers.
            |This might be a permissions issue or Docker daemon problem.
            |""".stripMargin
        )
      }
    } yield ()
}

