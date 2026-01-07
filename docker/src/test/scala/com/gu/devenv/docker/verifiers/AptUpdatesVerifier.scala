package com.gu.devenv.docker.verifiers

import com.gu.devenv.docker.testutils.DevcontainerRunner

/** Verifies that apt-updates module has run successfully.
  *
  * Checks that no packages are upgradeable after the postCreateCommand has run.
  */
object AptUpdatesVerifier {
  def verify(runner: DevcontainerRunner): Either[String, Unit] = {
    // Check that there are no upgradeable packages
    // apt-get -s upgrade simulates an upgrade and we count installable packages
    val result = runner.exec(
      """apt-get update -qq 2>/dev/null && apt list --upgradeable 2>/dev/null | grep -c upgradable || echo 0"""
    )

    if (result.failed) {
      Left(s"Failed to check for upgradeable packages: ${result.combinedOutput}")
    } else {
      val upgradeableCount = result.stdout.trim.toIntOption.getOrElse(-1)
      if (upgradeableCount == 0) {
        Right(())
      } else {
        Left(s"Expected 0 upgradeable packages but found $upgradeableCount")
      }
    }
  }
}
