package com.gu.devenv.e2e

import com.gu.devenv.e2e.testutils.{ContainerTest, DevcontainerTestSupport}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

/** E2E tests for the mise module.
  *
  * Verifies that mise is installed, configured, and tools are available.
  */
class MiseModuleTest extends AnyFreeSpec with Matchers with DevcontainerTestSupport {

  "mise module" - {
    "should install mise and make tools available" taggedAs ContainerTest in {
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

    "should have mise shims on the PATH" taggedAs ContainerTest in {
      val workspace = setupWorkspace("mise")

      startContainer(workspace) match {
        case Left(error) =>
          fail(s"Failed to start container: $error")

        case Right(runner) =>
          val pathResult = runner.exec("echo $PATH")
          pathResult.stdout should include("/mnt/mise-data/shims")
      }
    }

    "should have MISE_DATA_DIR set correctly" taggedAs ContainerTest in {
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

