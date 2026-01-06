package com.gu.devenv.docker

import com.gu.devenv.docker.verifiers.AptUpdatesVerifier
import com.gu.devenv.docker.testutils.{ContainerTest, DevcontainerTestSupport}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import java.nio.file.Files

/** Docker integration tests for the apt-updates module.
  *
  * Verifies that after container creation, all apt packages are up-to-date.
  */
class AptUpdatesModuleTest extends AnyFreeSpec with Matchers with DevcontainerTestSupport {
  "apt-updates module" - {
    "can set up workspace from fixture" in {
      val workspace = setupWorkspace("apt-updates")
      Files.isDirectory(workspace) shouldBe true
      Files.exists(workspace.resolve(".devcontainer/devenv.yaml")) shouldBe true
    }

    "devenv generation works" in {
      val workspace = setupWorkspace("apt-updates")
      runDevenvGenerate(workspace) match {
        case Left(error) =>
          fail(s"Generation failed: $error")
        case Right(_) =>
          Files.exists(workspace.resolve(".devcontainer/shared/devcontainer.json")) shouldBe true
          Files.exists(workspace.resolve(".devcontainer/user/devcontainer.json")) shouldBe true
      }
    }

    "should leave no upgradeable packages after container creation" taggedAs ContainerTest in {
      val workspace = setupWorkspace("apt-updates")

      startContainer(workspace) match {
        case Left(error) =>
          fail(s"Failed to start container: $error")

        case Right(runner) =>
          AptUpdatesVerifier.verify(runner) match {
            case Left(error) => fail(error)
            case Right(_)    => succeed
          }
      }
    }
  }
}
