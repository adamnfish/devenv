package com.gu.devenv.e2e

import com.gu.devenv.e2e.testutils.{ContainerTest, DevcontainerTestSupport}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

/** E2E tests for the docker-in-docker module.
  *
  * Verifies that Docker and Docker Compose work inside the container.
  */
class DockerInDockerModuleSpec extends AnyFunSpec with Matchers with DevcontainerTestSupport {

  describe("docker-in-docker module") {
    it("should have a working Docker installation", ContainerTest) {
      val workspace = setupWorkspace("docker-in-docker")

      startContainer(workspace) match {
        case Left(error) =>
          fail(s"Failed to start container: $error")

        case Right(runner) =>
          DockerInDockerAssertion.verify(runner) match {
            case Left(error) => fail(error)
            case Right(_)    => succeed
          }
      }
    }

    it("should be able to run docker containers", ContainerTest) {
      val workspace = setupWorkspace("docker-in-docker")

      startContainer(workspace) match {
        case Left(error) =>
          fail(s"Failed to start container: $error")

        case Right(runner) =>
          val result = runner.exec("docker run --rm hello-world")
          result.succeeded shouldBe true
          result.stdout should include("Hello from Docker!")
      }
    }
  }
}

