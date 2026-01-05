package com.gu.devenv.e2e.testutils

import java.nio.file.Path
import scala.sys.process.*

/** Result of running a command, either on the host or inside a container */
case class CommandResult(exitCode: Int, stdout: String, stderr: String) {
  def succeeded: Boolean = exitCode == 0
  def failed: Boolean    = !succeeded

  /** Combines stdout and stderr for logging */
  def combinedOutput: String =
    (if (stdout.nonEmpty) s"stdout:\n$stdout" else "") +
      (if (stderr.nonEmpty) s"\nstderr:\n$stderr" else "")
}

/** Runs devcontainer CLI commands and manages container lifecycle.
  *
  * Uses npx to run @devcontainers/cli, which will automatically download and cache the package if
  * needed.
  *
  * Note: devenv generates devcontainer.json files at .devcontainer/shared/devcontainer.json and
  * .devcontainer/user/devcontainer.json, so we use --config to point to the shared one.
  */
class DevcontainerRunner(workspaceDir: Path) {
  private val workspacePath = workspaceDir.toAbsolutePath.toString
  private val configPath =
    workspaceDir.resolve(".devcontainer/shared/devcontainer.json").toAbsolutePath.toString
  private val devcontainer = "npx --yes @devcontainers/cli"

  /** Builds the devcontainer image */
  def build(): CommandResult =
    runHostCommand(s"$devcontainer build --workspace-folder $workspacePath --config $configPath")

  /** Starts the devcontainer and returns its ID */
  def up(): Either[String, Unit] = {
    val result = runHostCommand(
      s"$devcontainer up --workspace-folder $workspacePath --config $configPath"
    )
    if (result.succeeded) {
      Right(())
    } else {
      Left(s"Failed to start container: ${result.combinedOutput}")
    }
  }

  /** Executes a command inside the running devcontainer */
  def exec(command: String): CommandResult =
    runHostCommand(
      s"""$devcontainer exec --workspace-folder $workspacePath --config $configPath -- bash -c '$command'"""
    )

  /** Executes a command inside the running devcontainer with a specific user */
  def execAsUser(command: String, user: String = "vscode"): CommandResult =
    runHostCommand(
      s"""$devcontainer exec --workspace-folder $workspacePath --config $configPath --remote-env USER=$user -- bash -c '$command'"""
    )

  /** Stops and removes the devcontainer */
  def down(): CommandResult = {
    // The devcontainer CLI doesn't have a down command, so we find and stop the container
    val findResult = runHostCommand(
      s"""docker ps -q --filter "label=devcontainer.local_folder=$workspacePath""""
    )
    if (findResult.succeeded && findResult.stdout.trim.nonEmpty) {
      val containerId = findResult.stdout.trim
      runHostCommand(s"docker rm -f $containerId")
    } else {
      // Container might not exist so this isn't necessarily a failure
      println(s"Warning: Could not find container to stop for workspace: $workspacePath")
      CommandResult(0, "", "")
    }
  }

  /** Cleans up any volumes created for this workspace */
  def cleanupVolumes(): CommandResult =
    // Clean up the mise data volume if it exists
    // runHostCommand("docker volume rm -f docker-mise-data-volume 2>/dev/null || true")
    // TODO: parameterize volume name so we can safely run cleanup from isolated tests
    CommandResult(0, "", "")

  private def runHostCommand(command: String): CommandResult = {
    val stdout = new StringBuilder
    val stderr = new StringBuilder
    val processLogger = ProcessLogger(
      line => stdout.append(line).append("\n"): Unit,
      line => stderr.append(line).append("\n"): Unit
    )
    val exitCode = command.!(processLogger)
    CommandResult(exitCode, stdout.toString.trim, stderr.toString.trim)
  }
}
