package com.gu.devenv.e2e

import com.gu.devenv.e2e.testutils.{ContainerTest, DevcontainerTestSupport}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

/** E2E tests for using multiple modules together.
  *
  * Verifies that all modules work correctly when combined.
  */
class CombinedModulesTest extends AnyFreeSpec with Matchers with DevcontainerTestSupport {

  "combined modules" - {
    "should work with apt-updates, mise, and docker-in-docker together" taggedAs ContainerTest in {
      val workspace = setupWorkspace("combined")

      startContainer(workspace) match {
        case Left(error) =>
          fail(s"Failed to start container: $error")

        case Right(runner) =>
          // Verify all modules
          val assertions = List(
            ("apt-updates", AptUpdatesAssertion.verify(runner)),
            ("mise", MiseAssertion.verify(runner)),
            ("docker-in-docker", DockerInDockerAssertion.verify(runner))
          )

          val failures = assertions.collect { case (name, Left(error)) =>
            s"$name: $error"
          }

          if (failures.nonEmpty) {
            fail(s"Module verification failures:\n${failures.mkString("\n")}")
          } else {
            succeed
          }
      }
    }
  }
}

