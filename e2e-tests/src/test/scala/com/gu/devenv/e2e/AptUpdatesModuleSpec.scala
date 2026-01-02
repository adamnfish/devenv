package com.gu.devenv.e2e

import com.gu.devenv.e2e.testutils.{ContainerTest, DevcontainerTestSupport}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

/** E2E tests for the apt-updates module.
  *
  * Verifies that after container creation, all apt packages are up-to-date.
  */
class AptUpdatesModuleSpec extends AnyFunSpec with Matchers with DevcontainerTestSupport {

  describe("apt-updates module") {
    it("should leave no upgradeable packages after container creation", ContainerTest) {
      val workspace = setupWorkspace("apt-updates")

      startContainer(workspace) match {
        case Left(error) =>
          fail(s"Failed to start container: $error")

        case Right(runner) =>
          AptUpdatesAssertion.verify(runner) match {
            case Left(error) => fail(error)
            case Right(_)    => succeed
          }
      }
    }
  }
}
