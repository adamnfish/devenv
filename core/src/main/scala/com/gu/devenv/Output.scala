package com.gu.devenv

import com.gu.devenv.Devenv.{CheckResult, FileDiff, GenerateResult, InitResult}
import com.gu.devenv.Filesystem.FileSystemStatus
import fansi.*

object Output {

  /** Formatting conventions:
    *   - Filenames/paths: Cyan
    *   - Commands: Bold Cyan
    *   - Status: Green (success), Light Gray (neutral), Red (error), Yellow (warning)
    *   - Code snippets: Bold Green
    *   - Section headings: Bold White
    *   - Warning/Error headings: Bold Yellow / Bold Red
    *   - Dividers: Light Blue
    */

  // Public API

  def initResultMessage(result: InitResult): String = {
    val table     = buildInitTable(result)
    val nextSteps = buildInitNextSteps()

    table + nextSteps
  }

  def generateResultMessage(result: GenerateResult): String =
    result match {
      case GenerateResult.Success(userStatus, sharedStatus) =>
        val table     = buildGenerateTable(userStatus, sharedStatus)
        val nextSteps = buildGenerateNextSteps()
        table + nextSteps

      case GenerateResult.NotInitialized =>
        buildNotInitializedMessage()

      case GenerateResult.ConfigNotCustomized =>
        buildConfigNotCustomizedMessage()
    }

  def checkResultMessage(result: CheckResult): String =
    result match {
      case CheckResult.Match(userPath, sharedPath) =>
        buildCheckMatchMessage(userPath, sharedPath)
      case CheckResult.Mismatch(userMismatch, sharedMismatch, userPath, sharedPath) =>
        buildCheckMismatchMessage(userMismatch, sharedMismatch, userPath, sharedPath)
      case CheckResult.NotInitialized =>
        buildNotInitializedMessage()
    }

  // Init message builders (called by initResultMessage)

  private def buildInitTable(result: InitResult): String = {
    val rows = List(
      (".devcontainer/", formatInitStatus(result.devcontainerStatus)),
      (".devcontainer/user/", formatInitStatus(result.userStatus)),
      (".devcontainer/shared/", formatInitStatus(result.sharedStatus)),
      (
        ".devcontainer/.gitignore",
        formatGitignoreStatus(result.gitignoreStatus)
      ),
      (".devcontainer/devenv.yaml", formatInitStatus(result.devenvStatus))
    )
    buildTable("Initialization Summary:", rows, 32)
  }

  private def buildInitNextSteps(): String =
    "\n\n" + Bold.On("Next steps:") + "\n" +
      s"  1. Edit ${Color.Cyan(".devcontainer/devenv.yaml")} to configure your project\n" +
      s"  2. Run ${Bold.On(Color.Cyan("devenv generate"))} to create devcontainer files"
  // Generate message builders (called by generateResultMessage)

  private def buildGenerateTable(
      userDevcontainerStatus: FileSystemStatus,
      sharedDevcontainerStatus: FileSystemStatus
  ): String = {
    val rows = List(
      (
        ".devcontainer/user/devcontainer.json",
        formatGenerateStatus(userDevcontainerStatus)
      ),
      (
        ".devcontainer/shared/devcontainer.json",
        formatGenerateStatus(sharedDevcontainerStatus)
      )
    )

    buildTable("Generation Summary:", rows, 47)
  }

  private def buildNotInitializedMessage(): String = {
    val header  = Bold.On(Color.Red("Project not initialized"))
    val divider = Color.Red("━" * 60)
    val message =
      s"\n${Color.Yellow("The .devcontainer directory has not been initialized.")}\n\n" +
        "Please complete these steps:\n" +
        s"  1. Run ${Bold.On(Color.Cyan("devenv init"))} to set up the project structure\n" +
        s"  2. Edit ${Color.Cyan(".devcontainer/devenv.yaml")} to configure your project\n" +
        s"  3. Run ${Bold.On(Color.Cyan("devenv generate"))} again to create devcontainer files"

    s"$header\n$divider$message"
  }

  private def buildConfigNotCustomizedMessage(): String = {
    val header  = Bold.On(Color.Yellow("Configuration not customized"))
    val divider = Color.Yellow("━" * 60)
    val message =
      s"\n${Color.Yellow("The devenv.yaml configuration file still contains the placeholder project name.")}\n\n" +
        s"Please edit ${Color.Cyan(".devcontainer/devenv.yaml")} and change:\n" +
        s"  ${Bold.On(Color.Red("name: \"CHANGE_ME\""))}\n" +
        "to:\n" +
        s"  ${Bold.On(Color.Green("name: \"Your Project Name\""))}\n\n" +
        s"Then run ${Bold.On(Color.Cyan("devenv generate"))} again."

    s"$header\n$divider$message"
  }

  private def buildGenerateNextSteps(): String =
    "\n\n" + Bold.On("You can now:") + "\n" +
      s"  • Open the project in your IDE and reopen in container\n" +
      s"  • Use the shared config for cloud-based development"

  // Check message builders (called by checkResultMessage)

  private def buildCheckMatchMessage(userPath: String, sharedPath: String): String = {
    val header  = Bold.On(Color.Green("✓ Configuration is up-to-date"))
    val divider = Color.Green("━" * 60)
    val message =
      s"\n${Color.Green("All devcontainer files match the current configuration.")}\n\n" +
        "Files checked:\n" +
        s"  ✓ ${Color.Cyan(userPath)}\n" +
        s"  ✓ ${Color.Cyan(sharedPath)}"

    s"$header\n$divider$message"
  }

  private def buildCheckMismatchMessage(
      userMismatch: Option[FileDiff],
      sharedMismatch: Option[FileDiff],
      userPath: String,
      sharedPath: String
  ): String = {
    val header  = Bold.On(Color.Red("✗ Configuration is out-of-date"))
    val divider = Color.Red("━" * 60)

    val mismatchedFiles = List(
      userMismatch.map(diff => s"  ✗ ${Color.Cyan(diff.path)}"),
      sharedMismatch.map(diff => s"  ✗ ${Color.Cyan(diff.path)}")
    ).flatten.mkString("\n")

    val matchedFiles = List(
      if (userMismatch.isEmpty) Some(s"  ✓ ${Color.Cyan(userPath)}") else None,
      if (sharedMismatch.isEmpty) Some(s"  ✓ ${Color.Cyan(sharedPath)}") else None
    ).flatten.mkString("\n")

    val filesSection = if (matchedFiles.nonEmpty) {
      s"Files out-of-date:\n$mismatchedFiles\n\nFiles up-to-date:\n$matchedFiles"
    } else {
      s"Files out-of-date:\n$mismatchedFiles"
    }

    val message =
      s"\n${Color.Yellow("The devcontainer files do not match the current configuration.")}\n\n" +
        filesSection + "\n\n" +
        s"Run ${Bold.On(Color.Cyan("devenv generate"))} to update the devcontainer files."

    s"$header\n$divider$message"
  }

  // Shared table builder (called by buildInitTable and buildGenerateTable)

  private def buildTable(
      title: String,
      rows: List[(String, (String, String, Str => Str))],
      pathPadding: Int
  ): String = {
    val header  = Bold.On(title)
    val divider = Color.LightBlue("━" * 60)

    val tableRows = rows
      .map { case (path, (emoji, text, colorFn)) =>
        val paddedPath = path.padTo(pathPadding, ' ')
        s"  $emoji ${Color.Cyan(paddedPath)} ${colorFn(text)}"
      }
      .mkString("\n")

    s"$header\n$divider\n$tableRows\n$divider"
  }

  // Status formatters (low-level helpers called by table builders)

  private def formatInitStatus(
      status: Filesystem.FileSystemStatus
  ): (String, String, Str => Str) = {
    import Filesystem.FileSystemStatus
    status match {
      case FileSystemStatus.Created => ("✅", "Created", s => Color.Green(s))
      case FileSystemStatus.AlreadyExists =>
        ("⚪", "Already exists", s => Color.LightGray(s))
    }
  }

  private def formatGitignoreStatus(
      status: Filesystem.GitignoreStatus
  ): (String, String, Str => Str) = {
    import Filesystem.GitignoreStatus
    status match {
      case GitignoreStatus.Created => ("✅", "Created", s => Color.Green(s))
      case GitignoreStatus.AlreadyExistsWithExclusion =>
        ("⚪", "Already exists", s => Color.LightGray(s))
      case GitignoreStatus.Updated =>
        ("🔄", "Updated", s => Color.Green(s))
    }
  }

  private def formatGenerateStatus(
      status: Filesystem.FileSystemStatus
  ): (String, String, Str => Str) = {
    import Filesystem.FileSystemStatus
    status match {
      case FileSystemStatus.Created => ("✅", "Created", s => Color.Green(s))
      case FileSystemStatus.AlreadyExists =>
        ("🔄", "Updated", s => Color.Green(s))
    }
  }
}
