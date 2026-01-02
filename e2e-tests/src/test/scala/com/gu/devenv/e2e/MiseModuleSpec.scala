package com.gu.devenv.e2e

import com.gu.devenv.e2e.testutils.{ContainerTest, DevcontainerTestSupport}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

/** E2E tests for the mise module.
  *
  * Verifies that mise is installed, configured, and tools are available.
  */
class MiseModuleSpec extends AnyFunSpec with Matchers with DevcontainerTestSupport {

  describe("mise module") {
    it("should install mise and make tools available", ContainerTest) {
      val workspace = setupWorkspace("mise")

      startContainer(workspace) match {
        case Left(error) =>
          fail(s"Failed to start container: $error")

        case Right(runner) =>
          MiseAssertion.verify(runner) match {
            case Left(error) => fail(error)
            case Right(_)    => succeed
          }
      }
    }

    it("should have mise shims on the PATH", ContainerTest) {
      val workspace = setupWorkspace("mise")

      startContainer(workspace) match {
        case Left(error) =>
          fail(s"Failed to start container: $error")

        case Right(runner) =>
          val pathResult = runner.exec("echo $PATH")
          pathResult.stdout should include("/mnt/mise-data/shims")
      }
    }

    it("should have MISE_DATA_DIR set correctly", ContainerTest) {
      val workspace = setupWorkspace("mise")

      startContainer(workspace) match {
        case Left(error) =>
          fail(s"Failed to start container: $error")

        case Right(runner) =>
          val envResult = runner.exec("echo $MISE_DATA_DIR")
          envResult.stdout.trim shouldBe "/mnt/mise-data"
      }
    }
  }
}

