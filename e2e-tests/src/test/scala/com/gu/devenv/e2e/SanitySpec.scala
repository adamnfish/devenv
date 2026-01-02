package com.gu.devenv.e2e

import com.gu.devenv.e2e.testutils.DevcontainerTestSupport
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import java.nio.file.Files

/** Quick sanity tests that don't require Docker.
  *
  * These tests verify that the test infrastructure is working correctly.
  */
class SanitySpec extends AnyFunSpec with Matchers with DevcontainerTestSupport {

  describe("test infrastructure") {
    it("can find the fixtures directory") {
      Files.isDirectory(fixturesDir) shouldBe true
    }

    it("can set up a workspace from a fixture") {
      val workspace = setupWorkspace("apt-updates")
      Files.isDirectory(workspace) shouldBe true
      Files.exists(workspace.resolve(".devcontainer/devenv.yaml")) shouldBe true
    }

    it("can run devenv generate") {
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
}

