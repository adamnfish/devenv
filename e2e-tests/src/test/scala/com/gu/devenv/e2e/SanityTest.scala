package com.gu.devenv.e2e

import com.gu.devenv.e2e.testutils.{ContainerTest, DevcontainerTestSupport, DockerChecker}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import java.nio.file.Files

/** Sanity checks of the testing setup and Docker availability.
  *
  * Run this test suite first to diagnose issues with the test environment before running the full
  * E2E suite.
  */
class SanityTest extends AnyFreeSpec with Matchers with DevcontainerTestSupport {

  "test infrastructure" - {
    "can find the fixtures directory" in {
      Files.isDirectory(fixturesDir) shouldBe true
    }

    "can set up a workspace from a fixture" in {
      val workspace = setupWorkspace("apt-updates")
      Files.isDirectory(workspace) shouldBe true
      Files.exists(workspace.resolve(".devcontainer/devenv.yaml")) shouldBe true
    }

    "can run devenv generate" in {
      val workspace = setupWorkspace("apt-updates")
      runDevenvGenerate(workspace) match {
        case Left(error) =>
          fail(s"Generation failed: $error")
        case Right(_) =>
          Files.exists(workspace.resolve(".devcontainer/shared/devcontainer.json")) shouldBe true
          Files.exists(workspace.resolve(".devcontainer/user/devcontainer.json")) shouldBe true
      }
    }
  }

  "docker availability" - {
    "docker should be installed and accessible" taggedAs ContainerTest in {
      DockerChecker.checkDockerAvailable() match {
        case Left(error) =>
          fail(
            s"""Docker is not available. All container tests will fail.
               |
               |$error
               |
               |Please ensure Docker Desktop is installed and running before running E2E tests.
               |""".stripMargin
          )
        case Right(_) => succeed
      }
    }
  }
}
