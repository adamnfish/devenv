package com.gu.devenv

import com.gu.devenv.Devenv.{GenerateResult, InitResult}
import com.gu.devenv.Filesystem.GitignoreStatus
import fansi._

object Output {

  // Public API
  def initResultMessage(result: InitResult): String = {
    val table = buildInitTable(result)
    val warning = buildGitignoreWarning(result.gitignoreStatus)
    val nextSteps = buildInitNextSteps(result.gitignoreStatus)

    List(Some(table), warning, Some(nextSteps)).flatten.mkString
  }

  def generateResultMessage(result: GenerateResult): String = {
    val table = buildGenerateTable(result)
    val nextSteps = buildGenerateNextSteps()

    table + nextSteps
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
      (".devcontainer/.devenv", formatInitStatus(result.devenvStatus))
    )
    buildTable("Initialization Summary:", rows, 32)
  }

  private def buildGitignoreWarning(status: GitignoreStatus): Option[String] = {
    status match {
      case GitignoreStatus.AlreadyExistsWithoutExclusion =>
        Some(
          s"\n\n${Bold.On(Color.Yellow("⚠️  WARNING ⚠️"))}\n" +
            Color.Yellow("━" * 60) + "\n" +
            Color.Red(
              "The .devcontainer/.gitignore file exists but does not\n" +
                "contain the required 'user/' exclusion!\n\n" +
                "This means your user-specific settings could be\n" +
                "committed to the repository.\n\n"
            ) +
            "Please add the following line to .devcontainer/.gitignore:\n" +
            s"  ${Color.Green("user/")}\n" +
            Color.Yellow("━" * 60)
        )
      case _ => None
    }
  }

  private def buildInitNextSteps(status: GitignoreStatus): String = {
    status match {
      case GitignoreStatus.AlreadyExistsWithoutExclusion => ""
      case _ =>
        "\n\n" + Bold.On("Next steps:") + "\n" +
          s"  ${Color.Green("1.")} Edit `.devcontainer/.devenv` to configure your project\n" +
          s"  ${Color.Green("2.")} Run 'devenv generate' to create devcontainer files"
    }
  }

  // Generate message builders (called by generateResultMessage)
  private def buildGenerateTable(result: GenerateResult): String = {
    val rows = List(
      (
        ".devcontainer/user/devcontainer.json",
        formatGenerateStatus(result.userDevcontainerStatus)
      ),
      (
        ".devcontainer/shared/devcontainer.json",
        formatGenerateStatus(result.sharedDevcontainerStatus)
      )
    )

    buildTable("Generation Summary:", rows, 47)
  }

  private def buildGenerateNextSteps(): String = {
    "\n\n" + Bold.On("You can now:") + "\n" +
      s"  ${Color.Green("•")} Open the project in your IDE and reopen in container\n" +
      s"  ${Color.Green("•")} Use the shared config for cloud-based development"
  }

  // Shared table builder (called by buildInitTable and buildGenerateTable)
  private def buildTable(
      title: String,
      rows: List[(String, (String, String, Str => Str))],
      pathPadding: Int
  ): String = {
    val header = Bold.On(title)
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
      case GitignoreStatus.AlreadyExistsWithoutExclusion =>
        ("❌", "No user/ entry", s => Color.Red(s))
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
